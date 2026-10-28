package ai.flowguard.experiment;

import ai.flowguard.oracles.OracleVerdict;

import java.util.List;
import java.util.Map;

public record ExperimentResult(
        Map<String, Object> before,
        Map<String, Object> after,
        List<Map<String, Object>> traces,
        String oracleVerdict,
        OracleVerdict oracleDetail,
        Map<String, Object> measurement
) {
}
