package com.sgkrashi.auth.controller;

import com.sgkrashi.auth.dto.request.ForgotPasswordRequest;
import com.sgkrashi.auth.dto.request.LoginRequest;
import com.sgkrashi.auth.dto.request.RegisterRequest;
import com.sgkrashi.auth.dto.request.ResetPasswordRequest;
import com.sgkrashi.auth.dto.response.AuthResponse;
import com.sgkrashi.auth.ratelimit.LoginRateLimiter;
import com.sgkrashi.auth.service.AuthResult;
import com.sgkrashi.auth.service.AuthService;
import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Registration, login, refresh-token rotation, logout, and password-reset stubs.
 * The refresh token is always issued/read as an HttpOnly cookie, never in a JSON body.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";
    private static final Duration REFRESH_COOKIE_MAX_AGE = Duration.ofDays(7);

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(AuthService authService, LoginRateLimiter loginRateLimiter) {
        this.authService = authService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        enforceRateLimit(servletRequest);
        AuthResult result = authService.register(request);
        setRefreshCookie(servletResponse, result.rawRefreshToken());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result.response(), "Registration successful"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        enforceRateLimit(servletRequest);
        AuthResult result = authService.login(request);
        setRefreshCookie(servletResponse, result.rawRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(result.response(), "Login successful"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse servletResponse
    ) {
        AuthResult result = authService.refresh(refreshToken);
        setRefreshCookie(servletResponse, result.rawRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(result.response(), "Session refreshed"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse servletResponse
    ) {
        authService.logout(refreshToken);
        clearRefreshCookie(servletResponse);
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                null, "If an account exists for that email, a reset link has been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password has been reset successfully"));
    }

    private void enforceRateLimit(HttpServletRequest request) {
        String clientKey = request.getRemoteAddr();
        if (!loginRateLimiter.tryConsume(clientKey)) {
            throw new RateLimitExceededException("Too many attempts. Please try again in a few minutes.");
        }
    }

    private void setRefreshCookie(HttpServletResponse response, String rawRefreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(REFRESH_COOKIE_MAX_AGE)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
