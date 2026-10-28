package ai.flowguard.experiment;

import ai.flowguard.config.FlowGuardProperties;
import ai.flowguard.oracles.FinancialOracle;
import ai.flowguard.oracles.InvariantId;
import ai.flowguard.oracles.OracleVerdict;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ExperimentExecutor {

    private final RestClient restClient;
    private final FlowGuardProperties properties;
    private final JdbcTemplate fixtureJdbc;

    public ExperimentExecutor(
            RestClient restClient,
            FlowGuardProperties properties,
            @Qualifier("fixtureJdbcTemplate") JdbcTemplate fixtureJdbc) {
        this.restClient = restClient;
        this.properties = properties;
        this.fixtureJdbc = fixtureJdbc;
    }

    public ExperimentResult execute(ValidatedExperiment experiment) {
        RestClientResponseException lastBusy = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return executeOnce(experiment);
            } catch (RestClientResponseException ex) {
                lastBusy = ex;
                if (!isAccountLockBusy(ex) || attempt == 3) {
                    throw ex;
                }
                releaseRaceQuietly();
                sleepQuietly(250L * attempt);
            }
        }
        throw lastBusy;
    }

    private ExperimentResult executeOnce(ValidatedExperiment experiment) {
        resetFixture();
        int parallel = ((Number) experiment.parameters().getOrDefault("parallelRequests", 2)).intValue();
        String checkpoint = experiment.parameters().getOrDefault("checkpoint", "AFTER_IDEMPOTENCY_LOOKUP").toString();
        long amountMinor = ((Number) experiment.parameters().getOrDefault("amountMinor", 10000)).longValue();
        String runSuffix = UUID.randomUUID().toString().substring(0, 8);
        String idempotencyKey = experiment.parameters().getOrDefault("idempotencyKey", "synthetic-ref-001")
                + "-" + runSuffix;

        configureRace(checkpoint, parallel);

        Map<String, Object> before = snapshotFixture();
        List<Map<String, Object>> traces = new ArrayList<>();

        if ("CONCURRENT_DUPLICATE".equals(experiment.experimentType())) {
            traces = runConcurrentCredits(parallel, amountMinor, idempotencyKey);
        } else if ("CONCURRENT_DEBIT".equals(experiment.experimentType())) {
            Map<String, Object> debitParams = new HashMap<>(experiment.parameters());
            debitParams.put("idempotencyKeyPrefix", debitParams.getOrDefault("idempotencyKeyPrefix", "debit-race") + "-" + runSuffix);
            traces = runConcurrentDebits(parallel, amountMinor, debitParams);
        } else if ("SAGA_CHOREOGRAPHY_VERIFY".equals(experiment.experimentType())) {
            traces = runSagaChoreographyVerify(amountMinor, idempotencyKey);
        } else {
            throw new InvalidExperimentException("Experiment not implemented: " + experiment.experimentType());
        }

        Map<String, Object> after = snapshotFixture();
        FinancialOracle oracle = new FinancialOracle(fixtureJdbc);
        InvariantId target = InvariantId.valueOf(mapInvariant(experiment.invariantId()));
        OracleVerdict verdict = oracle.verify(target);
        Map<String, Object> measurement = buildMeasurement(experiment, idempotencyKey, amountMinor, oracle, verdict);
        String oracleVerdict = verdict.passed() ? "NO_VIOLATION_OBSERVED" : "VIOLATION_REPRODUCED";

        return new ExperimentResult(before, after, traces, oracleVerdict, verdict, measurement);
    }

    private List<Map<String, Object>> runConcurrentDebits(int parallel, long amountMinor, Map<String, Object> parameters) {
        UUID source = UUID.fromString(properties.fixture().customerAccountId());
        UUID target = UUID.fromString("00000000-0000-4000-8000-000000000001");
        long fundMinor = ((Number) parameters.getOrDefault("fundMinor", 100_000L)).longValue();
        fundCustomer(fundMinor);

        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        try {
            List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
            for (int i = 0; i < parallel; i++) {
                String key = parameters.getOrDefault("idempotencyKeyPrefix", "debit-race") + "-" + i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    Map<String, Object> body = Map.of(
                            "sourceAccountId", source.toString(),
                            "targetAccountId", target.toString(),
                            "amountMinor", amountMinor,
                            "idempotencyKey", key);
                    try {
                        var response = restClient.post()
                                .uri(properties.fixture().baseUrl() + "/transfers")
                                .body(body)
                                .retrieve()
                                .body(Map.class);
                        return Map.of("thread", Thread.currentThread().getName(), "response", response);
                    } catch (RestClientResponseException ex) {
                        return Map.of(
                                "thread", Thread.currentThread().getName(),
                                "status", ex.getStatusCode().value(),
                                "response", ex.getResponseBodyAs(Map.class));
                    }
                }, pool));
            }
            return futures.stream().map(CompletableFuture::join).toList();
        } finally {
            pool.shutdownNow();
            restClient.post().uri(properties.fixture().baseUrl() + "/internal/race/release").retrieve().toBodilessEntity();
        }
    }

    private void fundCustomer(long fundMinor) {
        UUID target = UUID.fromString(properties.fixture().customerAccountId());
        Map<String, Object> body = Map.of(
                "clientReference", "prefund",
                "idempotencyKey", "prefund-" + UUID.randomUUID(),
                "targetAccountId", target.toString(),
                "amountMinor", fundMinor);
        restClient.post()
                .uri(properties.fixture().baseUrl() + "/payments")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private Map<String, Object> buildMeasurement(
            ValidatedExperiment experiment,
            String idempotencyKey,
            long amountMinor,
            FinancialOracle oracle,
            OracleVerdict verdict) {
        if ("CONCURRENT_DEBIT".equals(experiment.experimentType())) {
            return Map.of(
                    "currency", "USD",
                    "debitAmountMinor", amountMinor,
                    "invariantChecked", experiment.invariantId(),
                    "violationCount", verdict.violations().size());
        }
        long credited = oracle.sumCreditsForKey(idempotencyKey);
        return Map.of(
                "expectedMinor", amountMinor,
                "actualMinor", credited,
                "currency", "USD",
                "idempotencyKey", idempotencyKey);
    }

    private List<Map<String, Object>> runSagaChoreographyVerify(long amountMinor, String idempotencyKey) {
        UUID target = UUID.fromString(properties.fixture().customerAccountId());
        Map<String, Object> body = Map.of(
                "clientReference", "saga-probe-" + idempotencyKey,
                "idempotencyKey", idempotencyKey,
                "targetAccountId", target.toString(),
                "amountMinor", amountMinor);
        try {
            var response = restClient.post()
                    .uri(properties.fixture().baseUrl() + "/payments")
                    .header("Idempotency-Key", idempotencyKey)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return List.of(Map.of("response", response, "probe", "SAGA_CHOREOGRAPHY_VERIFY"));
        } catch (RestClientResponseException ex) {
            return List.of(Map.of(
                    "status", ex.getStatusCode().value(),
                    "response", ex.getResponseBodyAs(Map.class),
                    "probe", "SAGA_CHOREOGRAPHY_VERIFY"));
        }
    }

    private List<Map<String, Object>> runConcurrentCredits(int parallel, long amountMinor, String idempotencyKey) {
        UUID target = UUID.fromString(properties.fixture().customerAccountId());
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        try {
            List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
            for (int i = 0; i < parallel; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    Map<String, Object> body = Map.of(
                            "clientReference", "ref-" + idempotencyKey,
                            "idempotencyKey", idempotencyKey,
                            "targetAccountId", target.toString(),
                            "amountMinor", amountMinor);
                    try {
                        var response = restClient.post()
                                .uri(properties.fixture().baseUrl() + "/payments")
                                .body(body)
                                .retrieve()
                                .body(Map.class);
                        return Map.of("thread", Thread.currentThread().getName(), "response", response);
                    } catch (RestClientResponseException ex) {
                        return Map.of(
                                "thread", Thread.currentThread().getName(),
                                "status", ex.getStatusCode().value(),
                                "response", ex.getResponseBodyAs(Map.class));
                    }
                }, pool));
            }
            return futures.stream().map(CompletableFuture::join).toList();
        } finally {
            pool.shutdownNow();
            restClient.post().uri(properties.fixture().baseUrl() + "/internal/race/release").retrieve().toBodilessEntity();
        }
    }

    private static boolean isAccountLockBusy(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        return ex.getStatusCode().value() == 409
                && body != null
                && body.contains("ACCOUNT_LOCK_BUSY");
    }

    private void releaseRaceQuietly() {
        try {
            restClient.post().uri(properties.fixture().baseUrl() + "/internal/race/release").retrieve().toBodilessEntity();
        } catch (Exception ignored) {
            /* next reset will clear the fixture */
        }
        try {
            resetFixture();
        } catch (Exception ignored) {
            /* retry still proceeds */
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void resetFixture() {
        restClient.post().uri(properties.fixture().baseUrl() + "/internal/reset").retrieve().toBodilessEntity();
    }

    private void configureRace(String checkpoint, int parallel) {
        restClient.post()
                .uri(properties.fixture().baseUrl() + "/internal/race/configure")
                .body(Map.of("checkpoint", checkpoint, "parallelRequests", parallel))
                .retrieve()
                .toBodilessEntity();
    }

    private Map<String, Object> snapshotFixture() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("accounts", fixtureJdbc.queryForList("SELECT * FROM accounts ORDER BY id"));
        snap.put("payments", fixtureJdbc.queryForList("SELECT * FROM payments ORDER BY created_at"));
        snap.put("journalLines", fixtureJdbc.queryForList("""
                SELECT jl.* FROM journal_lines jl
                JOIN journals j ON j.id = jl.journal_id
                ORDER BY jl.id
                """));
        return snap;
    }

    private static String mapInvariant(String invariantId) {
        return switch (invariantId) {
            case "PAYMENT_EFFECT_ONCE" -> "PAYMENT_EFFECT_ONCE";
            case "ACCOUNT_RECONCILES" -> "ACCOUNT_RECONCILES";
            case "JOURNAL_BALANCED" -> "JOURNAL_BALANCED";
            case "NONNEGATIVE" -> "NONNEGATIVE";
            default -> "PAYMENT_EFFECT_ONCE";
        };
    }
}
