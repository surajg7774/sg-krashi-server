package com.sgkrashi.common.exception;

/**
 * Thrown when an operation violates a business rule. Maps to HTTP 422.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
