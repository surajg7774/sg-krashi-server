package com.sgkrashi.auth.controller;

import com.sgkrashi.auth.dto.request.GoogleAuthRequest;
import com.sgkrashi.auth.dto.request.LoginRequest;
import com.sgkrashi.auth.dto.request.RefreshTokenRequest;
import com.sgkrashi.auth.dto.request.VerifyOtpRequest;
import com.sgkrashi.auth.dto.response.MobileAuthResponse;
import com.sgkrashi.auth.dto.response.MobileRefreshResponse;
import com.sgkrashi.auth.ratelimit.LoginRateLimiter;
import com.sgkrashi.auth.service.AuthResult;
import com.sgkrashi.auth.service.AuthService;
import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mobile counterpart to {@code AuthController}'s login/refresh — added instead
 * of changing those, per the explicit decision that produced this class: a
 * native app (Expo/React Native — see the sg-krashi-mobile app) has no
 * cookie jar a server can rely on the way a browser's is relied on by
 * {@code AuthController}'s {@code SameSite=None} refresh cookie, so it needs
 * the refresh token transported in the JSON body instead. Deliberately a
 * separate controller/response-shape pair rather than a header-gated branch
 * inside {@code AuthController} itself — smaller diff, zero risk of
 * accidentally changing the existing cookie-based web behavior, and each
 * response shape only ever means one thing to its caller.
 *
 * <p>Delegates to the exact same {@link AuthService#login} / {@link
 * AuthService#refresh} the web path uses — same credential validation, same
 * {@code JwtTokenProvider} token issuance, same refresh-token rotation/revocation
 * in the DB. The only difference from {@code AuthController} is which half of
 * {@link AuthResult} (the JSON body vs. the {@code Set-Cookie} header) carries
 * the raw refresh token.
 */
@RestController
@RequestMapping("/api/v1/auth/mobile")
public class MobileAuthController {

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;

    public MobileAuthController(AuthService authService, LoginRateLimiter loginRateLimiter) {
        this.authService = authService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MobileAuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        enforceRateLimit(servletRequest);
        AuthResult result = authService.login(request);
        MobileAuthResponse body = new MobileAuthResponse(
                result.response().accessToken(), result.rawRefreshToken(), result.response().user());
        return ResponseEntity.ok(ApiResponse.success(body, "Login successful"));
    }

    // No rate limiting here either — LoginRateLimiter exists specifically to
    // slow down password-guessing, which doesn't apply to a Google ID token
    // (nothing to "guess"; an invalid one just fails verification, same as
    // any malformed credential elsewhere in this app).
    @PostMapping("/google")
    public ResponseEntity<ApiResponse<MobileAuthResponse>> google(@Valid @RequestBody GoogleAuthRequest request) {
        AuthResult result = authService.loginWithGoogle(request.idToken());
        MobileAuthResponse body = new MobileAuthResponse(
                result.response().accessToken(), result.rawRefreshToken(), result.response().user());
        return ResponseEntity.ok(ApiResponse.success(body, "Login successful"));
    }

    // No rate limiting — same reasoning as google() above: nothing to guess,
    // and TooManyOtpAttemptsException already bounds wrong-code attempts on
    // the OTP itself, which is the actual brute-force surface here.
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<MobileAuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthResult result = authService.verifyOtp(request);
        MobileAuthResponse body = new MobileAuthResponse(
                result.response().accessToken(), result.rawRefreshToken(), result.response().user());
        return ResponseEntity.ok(ApiResponse.success(body, "Account verified and created successfully"));
    }

    // No rate limiting here, matching AuthController.refresh(), which has
    // none either — only register()/login() are rate-limited on that
    // controller, so this stays at parity rather than inventing a stricter
    // rule the web path doesn't have.
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<MobileRefreshResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResult result = authService.refresh(request.refreshToken());
        MobileRefreshResponse body = new MobileRefreshResponse(result.response().accessToken(), result.rawRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(body, "Session refreshed"));
    }

    // Identical to AuthController's own private helper — duplicated rather
    // than extracted, since sharing it would mean introducing a base class or
    // a new shared component just for a three-line method used by two
    // controllers total.
    private void enforceRateLimit(HttpServletRequest request) {
        String clientKey = request.getRemoteAddr();
        if (!loginRateLimiter.tryConsume(clientKey)) {
            throw new RateLimitExceededException("Too many attempts. Please try again in a few minutes.");
        }
    }
}
