package ai.flowguard.experiment;

import java.util.Map;

public record ValidatedExperiment(String experimentType, String invariantId, Map<String, Object> parameters) {
}
