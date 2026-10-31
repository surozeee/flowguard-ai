package ai.flowguard.patch;

import org.springframework.stereotype.Service;

@Service
public class PatchValidationService {

    public void validateUnifiedDiff(String diff) {
        if (diff == null || diff.isBlank()) {
            throw new InvalidPatchException("Patch diff is empty");
        }
        boolean looksLikeDiff = diff.contains("---") || diff.contains("+++") || diff.contains("@@");
        if (!looksLikeDiff) {
            throw new InvalidPatchException("Patch must be a unified diff");
        }
    }
}
