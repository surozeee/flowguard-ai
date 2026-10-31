package ai.flowguard.patch.payload;

import java.util.UUID;

public record PatchProposeResult(
        UUID patchId,
        UUID investigationId,
        String status,
        String source,
        String diff
) {
}
