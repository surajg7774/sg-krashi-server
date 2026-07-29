package com.sgkrashi.common.exception;

/**
 * Thrown when a requested resource cannot be found. Maps to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
