package ai.flowguard.core.exception;

import ai.flowguard.core.enums.ApiStatusEnum;
import ai.flowguard.core.web.CorrelationIdFilter;
import ai.flowguard.core.payload.response.GlobalResponse;
import ai.flowguard.experiment.InvalidExperimentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidExperimentException.class)
    public ResponseEntity<GlobalResponse<Map<String, Object>>> invalidExperiment(InvalidExperimentException ex) {
        Map<String, Object> body = Map.of(
                "code", "EXPERIMENT_PARAMETER_OUT_OF_RANGE",
                "message", ex.getMessage(),
                "correlationId", UUID.randomUUID().toString(),
                "retryable", false);
        return ResponseEntity.badRequest().body(new GlobalResponse<>(
                ApiStatusEnum.VALIDATION_ERROR.name(), ex.getMessage(), body));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<GlobalResponse<Map<String, Object>>> accessDenied(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new GlobalResponse<>(
                ApiStatusEnum.ERROR.name(), "Access denied",
                Map.of("code", "ACCESS_DENIED", "correlationId", correlationId())));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<GlobalResponse<Map<String, Object>>> business(BusinessException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new GlobalResponse<>(
                ApiStatusEnum.ERROR.name(), ex.getMessage(),
                Map.of("code", ex.getCode(), "message", ex.getMessage(),
                        "correlationId", correlationId())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GlobalResponse<Map<String, Object>>> validation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new GlobalResponse<>(
                ApiStatusEnum.VALIDATION_ERROR.name(), "Validation failed",
                Map.of("code", "VALIDATION_ERROR", "message", details, "correlationId", correlationId())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GlobalResponse<Map<String, Object>>> unreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new GlobalResponse<>(
                ApiStatusEnum.VALIDATION_ERROR.name(), "Malformed request body",
                Map.of("code", "MALFORMED_JSON", "correlationId", correlationId())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GlobalResponse<Map<String, Object>>> fallback(Exception ex) {
        log.error("Unhandled API error correlationId={}", correlationId(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new GlobalResponse<>(
                ApiStatusEnum.ERROR.name(), "Internal server error",
                Map.of("code", "INTERNAL_ERROR", "correlationId", correlationId(), "retryable", true)));
    }

    private static String correlationId() {
        String id = org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY);
        return id == null ? UUID.randomUUID().toString() : id;
    }
}
