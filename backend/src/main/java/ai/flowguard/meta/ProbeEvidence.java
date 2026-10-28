package ai.flowguard.meta;

import java.util.Map;

public record ProbeEvidence(String experimentType, String verdict, Map<String, Object> measurement) {}
