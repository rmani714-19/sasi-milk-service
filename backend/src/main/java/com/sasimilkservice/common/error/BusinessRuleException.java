package com.sasimilkservice.common.error;

import org.springframework.http.HttpStatus;

/** 422 — the request is well-formed but violates a business rule. */
public class BusinessRuleException extends AppException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "business_rule_violation", message);
    }

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
