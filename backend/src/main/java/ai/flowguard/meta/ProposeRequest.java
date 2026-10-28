package ai.flowguard.meta;

import java.util.List;

public record ProposeRequest(
        String objective,
        int remainingProbes,
        List<ProbeEvidence> priorProbes,
        String preferredExperimentType
) {
    public ProposeRequest {
        priorProbes = priorProbes == null ? List.of() : priorProbes;
    }

    public ProposeRequest(String objective, int remainingProbes, List<ProbeEvidence> priorProbes) {
        this(objective, remainingProbes, priorProbes, null);
    }

    public static ProposeRequest of(String objective, int remainingProbes) {
        return new ProposeRequest(objective, remainingProbes, List.of(), null);
    }
}
