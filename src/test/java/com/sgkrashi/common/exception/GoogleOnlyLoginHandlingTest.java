package com.sgkrashi.common.exception;

import com.sgkrashi.auth.exception.GoogleOnlyAccountException;
import com.sgkrashi.common.dto.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InternalAuthenticationServiceException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reproduces the production 500: Spring Security wraps whatever
 * UserDetailsService throws in InternalAuthenticationServiceException, so a
 * Google-only account's password login never reached its own handler.
 */
class GoogleOnlyLoginHandlingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void aGoogleOnlyAccountPasswordLoginIsAProper401WithTheDocumentedMessage() {
        InternalAuthenticationServiceException wrapped = new InternalAuthenticationServiceException(
                "wrapper", new GoogleOnlyAccountException("This account uses Google Sign-In. Please log in with Google."));

        ResponseEntity<ApiErrorResponse> response = handler.handleInternalAuthenticationService(wrapped);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("GOOGLE_ONLY_ACCOUNT", response.getBody().error().code());
        assertEquals("This account uses Google Sign-In. Please log in with Google.", response.getBody().error().message());
    }

    @Test
    void aGenuineAuthenticationServiceFailureIsStillA500() {
        InternalAuthenticationServiceException wrapped =
                new InternalAuthenticationServiceException("db down", new IllegalStateException("db down"));

        ResponseEntity<ApiErrorResponse> response = handler.handleInternalAuthenticationService(wrapped);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().error().code());
    }
}
