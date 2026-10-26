package ai.flowguard.fixture.core.exception;

import ai.flowguard.fixture.concurrency.AccountLockException;
import ai.flowguard.fixture.idempotency.IdempotencyInProgressException;
import ai.flowguard.fixture.risk.RiskDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class FixtureExceptionHandler {

    @ExceptionHandler(RiskDeniedException.class)
    public ResponseEntity<Map<String, Object>> riskDenied(RiskDeniedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "code", ex.getCode(),
                "message", ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    public ResponseEntity<Map<String, Object>> idempotencyInProgress(IdempotencyInProgressException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "IDEMPOTENCY_IN_PROGRESS",
                "message", ex.getMessage()));
    }

    @ExceptionHandler(AccountLockException.class)
    public ResponseEntity<Map<String, Object>> accountLock(AccountLockException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "ACCOUNT_LOCK_BUSY",
                "message", ex.getMessage()));
    }
}
