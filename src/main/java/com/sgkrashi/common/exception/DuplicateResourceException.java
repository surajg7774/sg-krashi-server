package com.sgkrashi.common.exception;

/**
 * Thrown when a request would create a resource that violates a uniqueness
 * constraint (e.g. registering with an email that's already taken). Maps to HTTP 409.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
