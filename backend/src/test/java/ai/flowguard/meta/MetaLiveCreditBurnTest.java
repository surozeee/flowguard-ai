package ai.flowguard.meta;

import ai.flowguard.config.FlowGuardProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class MetaLiveCreditBurnTest {

    private static final int THREADS = 8;
    private static final int CALLS_PER_THREAD = 60;

    @Test
    @Timeout(value = 25, unit = TimeUnit.MINUTES)
    @EnabledIfEnvironmentVariable(named = "MODEL_API_KEY", matches = ".+")
    void consumeLiveMetaCreditsAcrossPlanExplainPatchAndSummary() throws Exception {
        String baseUrl = System.getenv().getOrDefault("MODEL_API_BASE_URL", "https://api.meta.ai/v1");
        String modelId = System.getenv().getOrDefault("MODEL_ID", "muse-spark-1.3");
        String apiKey = System.getenv("MODEL_API_KEY");
        MetaModelClient client = new MetaModelClient(
                propertiesWithMeta(baseUrl, apiKey, modelId), new ObjectMapper());

        AtomicInteger live = new AtomicInteger();
        AtomicInteger fallback = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        AtomicLong inTok = new AtomicLong();
        AtomicLong outTok = new AtomicLong();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            final int thread = t;
            tasks.add(() -> {
                for (int i = 0; i < CALLS_PER_THREAD; i++) {
                    int n = thread * CALLS_PER_THREAD + i;
                    try {
                        switch (n % 4) {
                            case 0 -> tally(client.proposeDetailed(ProposeRequest.of(planObjective(n), 3 - (n % 3))),
                                    live, fallback, inTok, outTok);
                            case 1 -> tally(client.completeJson(
                                    "Return JSON only. explanation must be detailed. Never change oracleVerdict.",
                                    explainPrompt(n),
                                    Map.of("headline", "stub", "explanation", "stub",
                                            "nextSuggestedExperimentType", "CONCURRENT_DUPLICATE")),
                                    live, fallback, inTok, outTok);
                            case 2 -> tally(client.completeJson(
                                    "Return JSON only with keys diff and rationale. diff must be a unified diff.",
                                    patchPrompt(n),
                                    Map.of("diff", "--- a/x\n+++ b/x\n@@\n+idx", "rationale", "stub")),
                                    live, fallback, inTok, outTok);
                            default -> tally(client.completeJson(
                                    "Return JSON only with keys summary and recommendedFollowUp.",
                                    summaryPrompt(n),
                                    Map.of("summary", "stub", "recommendedFollowUp", "stub")),
                                    live, fallback, inTok, outTok);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                }
                return null;
            });
        }

        List<Future<Void>> futures = pool.invokeAll(tasks);
        pool.shutdown();
        assertThat(pool.awaitTermination(24, TimeUnit.MINUTES)).isTrue();
        for (Future<Void> future : futures) {
            future.get();
        }

        int total = live.get() + fallback.get();
        System.out.printf(
                "Meta credit burn: live=%d fallback=%d errors=%d inputTokens=%d outputTokens=%d%n",
                live.get(), fallback.get(), errors.get(), inTok.get(), outTok.get());
        assertThat(total).isGreaterThanOrEqualTo(THREADS * CALLS_PER_THREAD);
        assertThat(live.get()).isGreaterThan(0);
    }

    private static void tally(
            MetaProposal proposal,
            AtomicInteger live,
            AtomicInteger fallback,
            AtomicLong inTok,
            AtomicLong outTok) {
        if (MetaProposal.LIVE.equals(proposal.source())) {
            live.incrementAndGet();
        } else {
            fallback.incrementAndGet();
        }
        if (proposal.inputTokens() != null) {
            inTok.addAndGet(proposal.inputTokens());
        }
        if (proposal.outputTokens() != null) {
            outTok.addAndGet(proposal.outputTokens());
        }
    }

    private static void tally(
            MetaJsonResult result,
            AtomicInteger live,
            AtomicInteger fallback,
            AtomicLong inTok,
            AtomicLong outTok) {
        if (MetaProposal.LIVE.equals(result.source())) {
            live.incrementAndGet();
        } else {
            fallback.incrementAndGet();
        }
        if (result.inputTokens() != null) {
            inTok.addAndGet(result.inputTokens());
        }
        if (result.outputTokens() != null) {
            outTok.addAndGet(result.outputTokens());
        }
    }

    private static String planObjective(int n) {
        return switch (n % 3) {
            case 0 -> "Detect duplicate credit under concurrent idempotency, case " + n;
            case 1 -> "Investigate concurrent debit ledger drift after a balance read, case " + n;
            default -> "Find saga choreography gaps after a settled transfer, case " + n;
        };
    }

    private static String explainPrompt(int n) {
        return """
                Objective: duplicate credit race case %d
                Experiment: CONCURRENT_DUPLICATE
                Oracle verdict (authoritative): NO_VIOLATION_OBSERVED
                Measurement: expectedMinor=10000 actualMinor=10000
                Return keys: headline, explanation, nextSuggestedExperimentType
                Write a long explanation covering race windows, idempotency keys, and why the oracle is authoritative.
                """.formatted(n);
    }

    private static String patchPrompt(int n) {
        return """
                Investigation objective: harden payments idempotency case %d
                State: COMPLETED
                Hypotheses: concurrent duplicate may both pass lookup
                Propose a unified diff plus rationale.
                """.formatted(n);
    }

    private static String summaryPrompt(int n) {
        return """
                Objective: at-most-once processing case %d
                Final state: COMPLETED
                Probes used: 2
                Probe evidence: CONCURRENT_DUPLICATE / NO_VIOLATION_OBSERVED
                Return keys: summary, recommendedFollowUp
                """.formatted(n);
    }

    private static FlowGuardProperties propertiesWithMeta(String baseUrl, String apiKey, String modelId) {
        return new FlowGuardProperties(
                null,
                new FlowGuardProperties.Meta(baseUrl, apiKey, modelId, false),
                null,
                null,
                null);
    }
}
