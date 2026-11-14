package com.sasimilkservice.common.error;

import com.sasimilkservice.rate.spi.RateNotConfiguredException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * Renders every failure as an RFC-7807 {@code ProblemDetail} with a stable
 * {@code code} property (spec api_design: "Return validation errors in a
 * consistent format", "Implement global exception handling").
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Known DB constraint names → friendly conflict messages. */
    private static final Map<String, String> CONSTRAINT_MESSAGES = Map.of(
            "ex_rate_chart_no_overlap", "An active rate chart already covers this period for this scope/region",
            "ex_settlement_no_overlap", "A settlement already covers an overlapping period",
            "milk_collections_client_transaction_id_key", "This collection was already submitted",
            "producers_producer_number_key", "Producer number already exists",
            "payouts_settlement_item_id_key", "A payout already exists for this settlement item",
            "payouts_idempotency_key_key", "Duplicate payout request");

    @ExceptionHandler(AppException.class)
    ProblemDetail handleApp(AppException ex) {
        return problem(ex.status(), ex.code(), ex.getMessage());
    }

    @ExceptionHandler(RateNotConfiguredException.class)
    ProblemDetail handleRate(RateNotConfiguredException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "rate_not_configured", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::fieldError)
                .toList();
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleStale(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "stale_version",
                "This record was modified by someone else. Reload and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        String raw = ex.getMostSpecificCause().getMessage();
        String friendly = CONSTRAINT_MESSAGES.entrySet().stream()
                .filter(e -> raw != null && raw.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("The request conflicts with existing data");
        log.debug("data integrity violation: {}", raw);
        return problem(HttpStatus.CONFLICT, "conflict", friendly);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Not permitted");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuth(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthorized", ex.getMessage());
    }

    /** A URL that does not exist is a 404, not a server error. */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ProblemDetail handleNotFound(Exception ex) {
        return problem(HttpStatus.NOT_FOUND, "not_found", "No such endpoint");
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ProblemDetail handleMethod(Exception ex) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed", "That method is not supported here");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Something went wrong");
    }

    private Map<String, String> fieldError(FieldError fe) {
        return Map.of(
                "field", fe.getField(),
                "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("code", code);
        return pd;
    }
}
