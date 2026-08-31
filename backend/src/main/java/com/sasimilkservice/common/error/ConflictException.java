package com.sasimilkservice.common.error;

import org.springframework.http.HttpStatus;

/** 409 — the request conflicts with current state (duplicate key, overlap, stale version). */
public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "conflict", message);
    }
}
