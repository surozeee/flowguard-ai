package ai.flowguard.evaluation;

import ai.flowguard.meta.InvestigationPlan;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomPlanGenerator {

    private RandomPlanGenerator() {
    }

    public static InvestigationPlan next(String objective) {
        boolean debit = ThreadLocalRandom.current().nextBoolean();
        if (debit) {
            return new InvestigationPlan(
                    "ACCOUNT_RECONCILES",
                    "Random probe: concurrent debit",
                    "RANDOM",
                    "CONCURRENT_DEBIT",
                    Map.of(
                            "parallelRequests", 2,
                            "amountMinor", 60_000,
                            "fundMinor", 100_000,
                            "checkpoint", "AFTER_BALANCE_READ",
                            "idempotencyKeyPrefix", "random-debit"),
                    "UNKNOWN",
                    "Random baseline for: " + objective);
        }
        return new InvestigationPlan(
                "PAYMENT_EFFECT_ONCE",
                "Random probe: concurrent duplicate credit",
                "RANDOM",
                "CONCURRENT_DUPLICATE",
                Map.of(
                        "parallelRequests", 2,
                        "amountMinor", 10_000,
                        "checkpoint", "AFTER_IDEMPOTENCY_LOOKUP",
                        "idempotencyKey", "random-" + ThreadLocalRandom.current().nextInt(10_000)),
                "UNKNOWN",
                "Random baseline for: " + objective);
    }
}
