package com.sasimilkservice.common.error;

import org.springframework.http.HttpStatus;

/** 404 — a referenced entity does not exist. */
public class NotFoundException extends AppException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "not_found", message);
    }

    public static NotFoundException of(String entity, Object id) {
        return new NotFoundException(entity + " " + id + " not found");
    }
}
