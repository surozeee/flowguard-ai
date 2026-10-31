package ai.flowguard.patch.payload;

import java.util.List;
import java.util.UUID;

public record PatchVerifyResult(
        UUID patchId,
        String buildStatus,
        String verdict,
        boolean booksStillBalance,
        String note,
        List<String> violations
) {}
