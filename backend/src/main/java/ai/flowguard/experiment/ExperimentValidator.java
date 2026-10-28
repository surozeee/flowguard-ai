package ai.flowguard.experiment;

import ai.flowguard.meta.InvestigationPlan;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class ExperimentValidator {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "CONCURRENT_DUPLICATE", "CONCURRENT_DEBIT", "SAGA_CHOREOGRAPHY_VERIFY");
    private static final Set<String> ALLOWED_CHECKPOINTS = Set.of(
            "AFTER_IDEMPOTENCY_LOOKUP", "AFTER_BALANCE_READ");
    private static final Set<String> ALLOWED_PARAM_KEYS = Set.of(
            "parallelRequests",
            "amountMinor",
            "checkpoint",
            "delayMillis",
            "sameIdempotencyKey",
            "idempotencyKey",
            "idempotencyKeyPrefix",
            "fundMinor");

    public ValidatedExperiment validate(InvestigationPlan plan) {
        if (!ALLOWED_TYPES.contains(plan.experimentType())) {
            throw new InvalidExperimentException("Unsupported experiment type: " + plan.experimentType());
        }
        Map<String, Object> params = whitelist(plan.parameters());
        if ("SAGA_CHOREOGRAPHY_VERIFY".equals(plan.experimentType())) {
            long amount = longParam(params, "amountMinor", 10000);
            if (amount < 1 || amount > 100_000) {
                throw new InvalidExperimentException("amountMinor out of range");
            }
            params.put("amountMinor", amount);
            return new ValidatedExperiment(plan.experimentType(), plan.invariantId(), params);
        }
        int parallel = intParam(params, "parallelRequests", 2);
        if (parallel < 2 || parallel > 4) {
            throw new InvalidExperimentException("parallelRequests must be between 2 and 4");
        }
        long amount = longParam(params, "amountMinor", 10000);
        if (amount < 1 || amount > 100_000) {
            throw new InvalidExperimentException("amountMinor out of range");
        }
        String checkpoint = stringParam(params, "checkpoint", "AFTER_IDEMPOTENCY_LOOKUP");
        if (!ALLOWED_CHECKPOINTS.contains(checkpoint)) {
            throw new InvalidExperimentException("Invalid checkpoint");
        }
        params.put("parallelRequests", parallel);
        params.put("amountMinor", amount);
        params.put("checkpoint", checkpoint);
        return new ValidatedExperiment(plan.experimentType(), plan.invariantId(), params);
    }

    private static Map<String, Object> whitelist(Map<String, Object> params) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (params == null) {
            return out;
        }
        for (String key : ALLOWED_PARAM_KEYS) {
            if (params.containsKey(key) && params.get(key) != null) {
                out.put(key, params.get(key));
            }
        }
        return out;
    }

    private static int intParam(Map<String, Object> params, String key, int defaultValue) {
        Object v = params.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long longParam(Map<String, Object> params, String key, long defaultValue) {
        Object v = params.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(v.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String stringParam(Map<String, Object> params, String key, String defaultValue) {
        Object v = params.get(key);
        return v == null ? defaultValue : v.toString();
    }
}
