package ai.flowguard.meta;

import ai.flowguard.config.FlowGuardProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class MetaModelClient implements MetaInvestigator {

    private static final Logger log = LoggerFactory.getLogger(MetaModelClient.class);
    public static final Set<String> ALLOWED_TYPES = Set.of(
            "CONCURRENT_DUPLICATE", "CONCURRENT_DEBIT", "SAGA_CHOREOGRAPHY_VERIFY");
    private static final int MAX_ATTEMPTS = 2;

    private final FlowGuardProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public MetaModelClient(FlowGuardProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public InvestigationPlan propose(String objective, int remainingProbes) {
        return proposeDetailed(ProposeRequest.of(objective, remainingProbes)).plan();
    }

    public MetaProposal proposeDetailed(ProposeRequest request) {
        InvestigationPlan fallback = defaultPlan(
                request.objective(), request.remainingProbes(), request.preferredExperimentType());
        MetaJsonResult raw = completeJson(
                "Return JSON only for a financial investigation plan using the allowed enums.",
                buildPrompt(request),
                objectMapper.convertValue(fallback, Map.class));
        try {
            InvestigationPlan parsed = objectMapper.treeToValue(raw.json(), InvestigationPlan.class);
            InvestigationPlan sanitized = sanitize(
                    parsed, request.objective(), request.remainingProbes(), request.preferredExperimentType());
            if (!ALLOWED_TYPES.contains(sanitized.experimentType())) {
                return MetaProposal.fallback(
                        fallback, raw.httpStatus(), raw.inputTokens(), raw.outputTokens(), "INVALID_PLAN");
            }
            return new MetaProposal(
                    sanitized,
                    raw.source(),
                    raw.httpStatus(),
                    raw.inputTokens(),
                    raw.outputTokens(),
                    raw.errorClass());
        } catch (Exception e) {
            return MetaProposal.fallback(
                    fallback, raw.httpStatus(), raw.inputTokens(), raw.outputTokens(), e.getClass().getSimpleName());
        }
    }

    public MetaJsonResult completeJson(String systemPrompt, String userPrompt, Map<String, Object> stubPayload) {
        JsonNode stubNode = objectMapper.valueToTree(stubPayload == null ? Map.of() : stubPayload);
        if (properties.meta().apiKey() == null || properties.meta().apiKey().isBlank()) {
            if (properties.meta().stubWhenNoKey()) {
                return new MetaJsonResult(stubNode, MetaProposal.STUB, null, null, null, null);
            }
            throw new ModelUnavailableException("MODEL_API_KEY not configured");
        }
        Integer lastStatus = null;
        Integer inTok = null;
        Integer outTok = null;
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", properties.meta().modelId(),
                    "messages", new Object[]{
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    },
                    "response_format", Map.of("type", "json_object")
            ));
            HttpResponse<String> response = sendWithRetry(body);
            lastStatus = response.statusCode();
            if (response.statusCode() >= 400) {
                log.warn("Meta API returned {}", response.statusCode());
                return new MetaJsonResult(
                        stubNode, MetaProposal.FALLBACK, lastStatus, inTok, outTok, "HTTP_" + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            inTok = intOrNull(root, "/usage/prompt_tokens");
            outTok = intOrNull(root, "/usage/completion_tokens");
            String content = root.at("/choices/0/message/content").asText("");
            JsonNode parsed = objectMapper.readTree(extractJson(content));
            return new MetaJsonResult(parsed, MetaProposal.LIVE, lastStatus, inTok, outTok, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new MetaJsonResult(stubNode, MetaProposal.FALLBACK, lastStatus, inTok, outTok, "INTERRUPTED");
        } catch (Exception e) {
            log.warn("Meta API call failed, using stub payload", e);
            return new MetaJsonResult(
                    stubNode, MetaProposal.FALLBACK, lastStatus, inTok, outTok, e.getClass().getSimpleName());
        }
    }

    private static String buildPrompt(ProposeRequest request) {
        StringBuilder prior = new StringBuilder();
        List<ProbeEvidence> probes = request.priorProbes() == null ? List.of() : request.priorProbes();
        if (probes.isEmpty()) {
            prior.append("None yet.");
        } else {
            int i = 1;
            for (ProbeEvidence probe : probes) {
                prior.append(i++)
                        .append(". type=")
                        .append(probe.experimentType())
                        .append(" verdict=")
                        .append(probe.verdict())
                        .append(" measurement=")
                        .append(probe.measurement())
                        .append('\n');
            }
        }
        return """
                Objective: %s
                Remaining probes: %d
                Allowed experimentType exactly one of: CONCURRENT_DUPLICATE, CONCURRENT_DEBIT, SAGA_CHOREOGRAPHY_VERIFY.
                Allowed invariantId: PAYMENT_EFFECT_ONCE or ACCOUNT_RECONCILES.
                Allowed checkpoint: AFTER_IDEMPOTENCY_LOOKUP or AFTER_BALANCE_READ.
                suspectedMechanism must be a short snake or SCREAMING_SNAKE token (max 80 chars), not a paragraph.
                Preferred next experimentType (from the last Meta explain; pick this unless prior probes already used it): %s
                Prior probe evidence (oracles already decided these; do not contradict them, pick the next experiment):
                %s
                Return JSON only with keys: invariantId, hypothesis, suspectedMechanism, experimentType, parameters,
                predictedViolation, reasonForChoice.
                parameters must include parallelRequests (2-4) when racing, amountMinor (10000), and checkpoint.
                """.formatted(
                request.objective(),
                request.remainingProbes(),
                request.preferredExperimentType() == null || request.preferredExperimentType().isBlank()
                        ? "none"
                        : request.preferredExperimentType(),
                prior);
    }

    public static InvestigationPlan defaultPlan(String objective) {
        return defaultPlan(objective, 1);
    }

    public static InvestigationPlan defaultPlan(String objective, int remainingProbes) {
        return defaultPlan(objective, remainingProbes, null);
    }

    public static InvestigationPlan defaultPlan(String objective, int remainingProbes, String preferredExperimentType) {
        int probeIndex = Math.max(1, remainingProbes);
        if (preferredExperimentType != null && ALLOWED_TYPES.contains(preferredExperimentType)) {
            return switch (preferredExperimentType) {
                case "CONCURRENT_DEBIT" -> debitDefaultPlan(objective);
                case "SAGA_CHOREOGRAPHY_VERIFY" -> sagaChoreographyPlan(objective, probeIndex);
                default -> creditDefaultPlan(objective, probeIndex);
            };
        }
        if (objective != null && objective.toLowerCase().contains("saga")) {
            return sagaChoreographyPlan(objective, probeIndex);
        }
        if (objective != null && objective.toLowerCase().contains("debit")) {
            return debitDefaultPlan(objective);
        }
        return creditDefaultPlan(objective, probeIndex);
    }

    static InvestigationPlan sagaChoreographyPlan(String objective, int probeIndex) {
        return new InvestigationPlan(
                "PAYMENT_EFFECT_ONCE",
                "Saga choreography should record start and terminal steps for settled payments.",
                "SAGA_ORCHESTRATION",
                "SAGA_CHOREOGRAPHY_VERIFY",
                Map.of(
                        "amountMinor", 10000,
                        "idempotencyKey", "saga-probe-" + probeIndex
                ),
                "MISSING_SAGA_TERMINAL",
                "Saga trace probe for objective: " + objective
        );
    }

    static InvestigationPlan creditDefaultPlan(String objective, int probeIndex) {
        return new InvestigationPlan(
                "PAYMENT_EFFECT_ONCE",
                "Concurrent duplicates may both pass idempotency lookup before insert commits.",
                "CHECK_THEN_ACT_RACE",
                "CONCURRENT_DUPLICATE",
                Map.of(
                        "parallelRequests", 2,
                        "amountMinor", 10000,
                        "sameIdempotencyKey", true,
                        "checkpoint", "AFTER_IDEMPOTENCY_LOOKUP",
                        "delayMillis", 0,
                        "idempotencyKey", "synthetic-ref-" + probeIndex
                ),
                "DUPLICATE_CREDIT",
                "Default credit plan for objective: " + objective
        );
    }

    static InvestigationPlan debitDefaultPlan(String objective) {
        return new InvestigationPlan(
                "ACCOUNT_RECONCILES",
                "Concurrent debits may both observe the same balance before write-back.",
                "CHECK_THEN_ACT_RACE",
                "CONCURRENT_DEBIT",
                Map.of(
                        "parallelRequests", 2,
                        "amountMinor", 60000,
                        "fundMinor", 100000,
                        "checkpoint", "AFTER_BALANCE_READ",
                        "idempotencyKeyPrefix", "debit-race"
                ),
                "LEDGER_DRIFT",
                "Default debit plan for objective: " + objective
        );
    }

    private HttpResponse<String> sendWithRetry(String body) throws Exception {
        HttpResponse<String> last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(trimBase(properties.meta().baseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", "Bearer " + properties.meta().apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            last = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (!retryable(last.statusCode()) || attempt == MAX_ATTEMPTS) {
                return last;
            }
            log.info("Meta API {} on attempt {}, retrying", last.statusCode(), attempt);
            TimeUnit.MILLISECONDS.sleep(400L * attempt);
        }
        return last;
    }

    private static boolean retryable(int status) {
        return status == 429 || status == 502 || status == 503 || status == 504;
    }

    private static String trimBase(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.meta.ai/v1";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    static String extractJson(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("empty model content");
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int newline = trimmed.indexOf('\n');
            int endFence = trimmed.lastIndexOf("```");
            if (newline > 0 && endFence > newline) {
                trimmed = trimmed.substring(newline + 1, endFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    static InvestigationPlan sanitize(InvestigationPlan raw, String objective, int remainingProbes) {
        return sanitize(raw, objective, remainingProbes, null);
    }

    static InvestigationPlan sanitize(
            InvestigationPlan raw, String objective, int remainingProbes, String preferredExperimentType) {
        InvestigationPlan defaults = defaultPlan(objective, remainingProbes, preferredExperimentType);
        if (raw == null) {
            return defaults;
        }
        String type = normalizeType(raw.experimentType(), objective);
        Map<String, Object> params = new LinkedHashMap<>();
        if (defaults.parameters() != null) {
            params.putAll(defaults.parameters());
        }
        if (raw.parameters() != null) {
            params.putAll(raw.parameters());
        }
        return new InvestigationPlan(
                firstNonBlank(raw.invariantId(), defaults.invariantId()),
                firstNonBlank(raw.hypothesis(), defaults.hypothesis()),
                firstNonBlank(raw.suspectedMechanism(), defaults.suspectedMechanism()),
                type,
                params,
                firstNonBlank(raw.predictedViolation(), defaults.predictedViolation()),
                firstNonBlank(raw.reasonForChoice(), defaults.reasonForChoice()));
    }

    static String normalizeType(String experimentType, String objective) {
        if (experimentType != null && ALLOWED_TYPES.contains(experimentType.trim())) {
            return experimentType.trim();
        }
        String blob = ((experimentType == null ? "" : experimentType) + " " + (objective == null ? "" : objective))
                .toLowerCase();
        if (blob.contains("saga")) {
            return "SAGA_CHOREOGRAPHY_VERIFY";
        }
        if (blob.contains("debit")) {
            return "CONCURRENT_DEBIT";
        }
        return "CONCURRENT_DUPLICATE";
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Integer intOrNull(JsonNode root, String pointer) {
        JsonNode node = root.at(pointer);
        if (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asInt();
    }
}
