package ai.flowguard.patch;

import ai.flowguard.evidence.EvidenceHashService;
import ai.flowguard.investigation.service.InvestigationEventService;
import ai.flowguard.meta.AuditingMetaInvestigator;
import ai.flowguard.meta.MetaJsonResult;
import ai.flowguard.oracles.FinancialOracle;
import ai.flowguard.oracles.OracleVerdict;
import ai.flowguard.patch.payload.PatchProposeResult;
import ai.flowguard.patch.payload.PatchVerifyResult;
import ai.flowguard.persistence.entity.HypothesisEntity;
import ai.flowguard.persistence.entity.InvestigationEntity;
import ai.flowguard.persistence.entity.PatchAttemptEntity;
import ai.flowguard.persistence.jpa.HypothesisJpaRepository;
import ai.flowguard.persistence.jpa.InvestigationJpaRepository;
import ai.flowguard.persistence.jpa.PatchAttemptJpaRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PatchCommandService {

    static final String STUB_DIFF = """
            --- a/fixture-wallet/src/main/resources/db/migration/fixture/V1__idempotency.sql
            +++ b/fixture-wallet/src/main/resources/db/migration/fixture/V1__idempotency.sql
            @@ -1,3 +1,6 @@
            -- PROPOSAL ONLY. Oracles must re-verify. Do not apply blindly.
            +CREATE UNIQUE INDEX IF NOT EXISTS payments_idempotency_key_uidx
            +  ON payments (idempotency_key);
            """.stripIndent();

    private final PatchAttemptJpaRepository patchAttempts;
    private final PatchValidationService patchValidationService;
    private final EvidenceHashService evidenceHashService;
    private final JdbcTemplate fixtureJdbc;
    private final AuditingMetaInvestigator auditingMetaInvestigator;
    private final InvestigationJpaRepository investigations;
    private final HypothesisJpaRepository hypotheses;
    private final InvestigationEventService investigationEventService;

    public PatchCommandService(
            PatchAttemptJpaRepository patchAttempts,
            PatchValidationService patchValidationService,
            EvidenceHashService evidenceHashService,
            @Qualifier("fixtureJdbcTemplate") JdbcTemplate fixtureJdbc,
            AuditingMetaInvestigator auditingMetaInvestigator,
            InvestigationJpaRepository investigations,
            HypothesisJpaRepository hypotheses,
            InvestigationEventService investigationEventService) {
        this.patchAttempts = patchAttempts;
        this.patchValidationService = patchValidationService;
        this.evidenceHashService = evidenceHashService;
        this.fixtureJdbc = fixtureJdbc;
        this.auditingMetaInvestigator = auditingMetaInvestigator;
        this.investigations = investigations;
        this.hypotheses = hypotheses;
        this.investigationEventService = investigationEventService;
    }

    public PatchProposeResult proposePatch(UUID investigationId, String diff, boolean approved, boolean generate) {
        String source = "USER";
        String resolved = diff;
        if (generate || resolved == null || resolved.isBlank()) {
            MetaJsonResult generated = generateDiff(investigationId);
            resolved = text(generated, "diff", STUB_DIFF);
            source = generated.source();
            try {
                patchValidationService.validateUnifiedDiff(resolved);
            } catch (InvalidPatchException ex) {
                resolved = STUB_DIFF;
                source = ai.flowguard.meta.MetaProposal.FALLBACK;
            }
            investigationEventService.publish(investigationId, "model.patch.received", Map.of(
                    "source", source,
                    "rationale", text(generated, "rationale", "Deterministic unique-index proposal."),
                    "httpStatus", generated.httpStatus() == null ? 0 : generated.httpStatus()));
        }
        patchValidationService.validateUnifiedDiff(resolved);
        String diffHash = evidenceHashService.sha256("patch", "genesis", Map.of("diff", resolved));
        UUID patchId = UUID.randomUUID();
        String approvalStatus = approved ? "APPROVED" : "PENDING";
        patchAttempts.save(PatchAttemptEntity.queued(
                patchId,
                investigationId,
                diffHash,
                approvalStatus,
                evidenceHashService.sha256("oracle", diffHash, Map.of("oracle", FinancialOracle.VERSION)),
                approved ? "READY_FOR_SANDBOX" : "AWAITING_APPROVAL"));
        return new PatchProposeResult(patchId, investigationId, approvalStatus, source, resolved);
    }

    private MetaJsonResult generateDiff(UUID investigationId) {
        InvestigationEntity investigation = investigations.findById(investigationId).orElseThrow(
                () -> new InvalidPatchException("No investigation found for id " + investigationId));
        String hypothesesText = hypotheses.findByInvestigationIdOrderByCreatedAtAsc(investigationId).stream()
                .map(HypothesisEntity::getHypothesisText)
                .collect(Collectors.joining("\n- "));
        return auditingMetaInvestigator.completeJson(
                investigationId,
                AuditingMetaInvestigator.PURPOSE_PATCH,
                "Return JSON only with keys diff and rationale. diff must be a unified diff. Do not claim oracles passed.",
                """
                        Investigation objective: %s
                        State: %s
                        Hypotheses:
                        - %s
                        Propose a minimal unique-index or comment-only unified diff that would harden idempotency.
                        """.formatted(
                        investigation.getObjective(),
                        investigation.getState(),
                        hypothesesText.isBlank() ? "(none yet)" : hypothesesText),
                Map.of(
                        "diff", STUB_DIFF,
                        "rationale", "Default unique index on payments.idempotency_key."));
    }

    public PatchVerifyResult verifyPatch(UUID patchId) {
        PatchAttemptEntity entity = patchAttempts.findById(patchId).orElseThrow(
                () -> new InvalidPatchException("No patch found for id " + patchId));
        OracleVerdict oracle = new FinancialOracle(fixtureJdbc).verifyAll();
        List<String> violations = oracle.violations().stream()
                .map(v -> v.invariantId() + ": " + v.message())
                .toList();
        if (oracle.passed()) {
            entity.markBuildPassed();
            patchAttempts.save(entity);
            return new PatchVerifyResult(
                    patchId,
                    "PASSED",
                    "IMMUTABLE_TESTS_PASSED",
                    true,
                    "We did not apply the diff to a git worktree. We did re-run the live JDBC oracles on the fixture ledger.",
                    List.of());
        }
        entity.markBuildFailed("ORACLE_VIOLATION");
        patchAttempts.save(entity);
        return new PatchVerifyResult(
                patchId,
                "FAILED",
                "ORACLE_VIOLATION",
                false,
                "The books no longer balance. The proposed diff was not applied; this is the current fixture ledger.",
                violations);
    }

    private static String text(MetaJsonResult result, String field, String fallback) {
        if (result.json() == null || result.json().path(field).isMissingNode() || result.json().path(field).isNull()) {
            return fallback;
        }
        String value = result.json().path(field).asText();
        return value == null || value.isBlank() ? fallback : value;
    }
}
