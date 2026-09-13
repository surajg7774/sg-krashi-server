package com.sgkrashi.auth.controller;

import com.sgkrashi.auth.dto.request.ForgotPasswordRequest;
import com.sgkrashi.auth.dto.request.LoginRequest;
import com.sgkrashi.auth.dto.request.RegisterRequest;
import com.sgkrashi.auth.dto.request.ResetPasswordRequest;
import com.sgkrashi.auth.dto.request.VerifyEmailRequest;
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
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        enforceRateLimit(servletRequest);
        authService.register(request);
        return ResponseEntity.ok(ApiResponse.success(
                null, "Please check your email to verify your account and finish creating it"));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request,
            HttpServletResponse servletResponse
    ) {
        AuthResult result = authService.verifyEmail(request);
        setRefreshCookie(servletResponse, result.rawRefreshToken());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result.response(), "Account verified and created successfully"));
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

    /**
     * {@code SameSite=None} (not {@code Strict} or the default {@code Lax}) is
     * required here, not a stylistic choice: the frontend (Vercel) and this API
     * (Railway) are different sites, so every fetch/XHR call between them —
     * including this cookie's own round trip on {@code /refresh} — is
     * cross-site. Browsers withhold {@code Strict} cookies from ALL cross-site
     * requests (not just top-level navigations, which is the {@code Lax}
     * carve-out), so the refresh call always sent no cookie at all and this
     * endpoint always saw {@code refreshToken == null} — a full page reload
     * silently logged every user out. Confirmed live: the outgoing
     * {@code /auth/refresh} request carried no {@code Cookie} header
     * whatsoever, not merely an invalid one.
     *
     * <p>{@code SameSite=None} requires {@code Secure} (already set) and does
     * remove the CSRF protection {@code Strict}/{@code Lax} provide — a
     * third-party page can now trigger a cross-site POST to this cookie's path
     * that carries it. Two things keep that low-severity here: the cookie's
     * {@code path} is scoped to {@code /api/v1/auth} only (never reaches any
     * other endpoint), and {@link com.sgkrashi.config.CorsConfig} is a strict,
     * non-wildcard origin allow-list, so a forged cross-site call can rotate
     * this cookie (a nuisance forced-logout/forced-refresh) but can never let
     * an attacker's own JavaScript read the response — {@code
     * Access-Control-Allow-Origin} would have to name the attacker's origin
     * for that, and it never does.
     */
    private void setRefreshCookie(HttpServletResponse response, String rawRefreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(REFRESH_COOKIE_MAX_AGE)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** Same {@code SameSite=None} reasoning as {@link #setRefreshCookie} — must match exactly, or the browser treats this as a different cookie and never clears the original one on cross-site logout calls. */
    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
