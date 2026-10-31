package ai.flowguard.patch;

import ai.flowguard.core.exception.BusinessException;

public class InvalidPatchException extends BusinessException {

    public InvalidPatchException(String message) {
        super("INVALID_PATCH", message);
    }
}
