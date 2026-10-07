package com.sgkrashi.auth.controller;

import com.sgkrashi.auth.dto.request.GoogleAuthRequest;
import com.sgkrashi.auth.dto.request.LoginRequest;
import com.sgkrashi.auth.dto.request.RefreshTokenRequest;
import com.sgkrashi.auth.dto.request.VerifyOtpRequest;
import com.sgkrashi.auth.dto.response.MobileAuthResponse;
import com.sgkrashi.auth.dto.response.MobileRefreshResponse;
import com.sgkrashi.auth.ratelimit.AuthRateLimiters;
import com.sgkrashi.auth.ratelimit.LoginRateLimiter;
import com.sgkrashi.auth.service.AuthResult;
import com.sgkrashi.auth.service.AuthService;
import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.web.ClientIpResolver;
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
    private final AuthRateLimiters authRateLimiters;
    private final ClientIpResolver clientIpResolver;

    public MobileAuthController(
            AuthService authService,
            LoginRateLimiter loginRateLimiter,
            AuthRateLimiters authRateLimiters,
            ClientIpResolver clientIpResolver
    ) {
        this.authService = authService;
        this.loginRateLimiter = loginRateLimiter;
        this.authRateLimiters = authRateLimiters;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MobileAuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        loginRateLimiter.enforce(clientIpResolver.resolve(servletRequest));
        AuthResult result = authService.login(request);
        MobileAuthResponse body = new MobileAuthResponse(
                result.response().accessToken(), result.rawRefreshToken(), result.response().user());
        return ResponseEntity.ok(ApiResponse.success(body, "Login successful"));
    }

    // Not covered by LoginRateLimiter (that one slows password guessing, and there is nothing to
    // guess in a Google ID token), but still capped per client IP by AuthRateLimiters so one
    // address cannot make the server verify tokens against Google without limit.
    @PostMapping("/google")
    public ResponseEntity<ApiResponse<MobileAuthResponse>> google(
            @Valid @RequestBody GoogleAuthRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiters.checkGoogle(clientIpResolver.resolve(servletRequest));
        AuthResult result = authService.loginWithGoogle(request.idToken());
        MobileAuthResponse body = new MobileAuthResponse(
                result.response().accessToken(), result.rawRefreshToken(), result.response().user());
        return ResponseEntity.ok(ApiResponse.success(body, "Login successful"));
    }

    // TooManyOtpAttemptsException bounds wrong-code attempts on the OTP itself, which is the
    // actual brute-force surface here; the per-IP cap in AuthRateLimiters stops one address
    // from walking through many pending registrations.
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<MobileAuthResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiters.checkVerifyOtp(clientIpResolver.resolve(servletRequest));
        AuthResult result = authService.verifyOtp(request);
        MobileAuthResponse body = new MobileAuthResponse(
                result.response().accessToken(), result.rawRefreshToken(), result.response().user());
        return ResponseEntity.ok(ApiResponse.success(body, "Account verified and created successfully"));
    }

    // Capped per client IP by AuthRateLimiters (generous: a refresh is normally one call per user
    // every few minutes, and many users share one carrier-NAT address). The web refresh, which runs
    // on every page load for every visitor, is deliberately left unlimited.
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<MobileRefreshResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiters.checkRefresh(clientIpResolver.resolve(servletRequest));
        AuthResult result = authService.refresh(request.refreshToken());
        MobileRefreshResponse body = new MobileRefreshResponse(result.response().accessToken(), result.rawRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(body, "Session refreshed"));
    }

}
