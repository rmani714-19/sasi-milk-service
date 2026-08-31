package com.sasimilkservice.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base for expected, client-facing failures. Each subclass fixes an HTTP status
 * and a short machine-readable {@code code}; {@link GlobalExceptionHandler}
 * renders them as RFC-7807 {@code ProblemDetail}s.
 */
public abstract class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected AppException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
