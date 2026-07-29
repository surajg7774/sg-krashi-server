package com.sgkrashi.common.exception;

/**
 * Thrown when request data fails business/semantic validation. Maps to HTTP 400.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
