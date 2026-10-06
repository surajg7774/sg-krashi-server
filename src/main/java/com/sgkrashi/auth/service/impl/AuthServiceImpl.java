package com.sgkrashi.auth.service.impl;

import com.sgkrashi.auth.dto.request.ForgotPasswordRequest;
import com.sgkrashi.auth.dto.request.LoginRequest;
import com.sgkrashi.auth.dto.request.RegisterRequest;
import com.sgkrashi.auth.dto.request.ResendOtpRequest;
import com.sgkrashi.auth.dto.request.ResetPasswordRequest;
import com.sgkrashi.auth.dto.request.VerifyOtpRequest;
import com.sgkrashi.auth.dto.response.AuthResponse;
import com.sgkrashi.auth.entity.PendingRegistration;
import com.sgkrashi.auth.entity.RefreshToken;
import com.sgkrashi.auth.entity.Role;
import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.exception.InvalidOtpException;
import com.sgkrashi.auth.exception.OtpResendCooldownException;
import com.sgkrashi.auth.exception.TooManyOtpAttemptsException;
import com.sgkrashi.auth.mapper.UserMapper;
import com.sgkrashi.auth.repository.PendingRegistrationRepository;
import com.sgkrashi.auth.repository.RefreshTokenRepository;
import com.sgkrashi.auth.repository.RoleRepository;
import com.sgkrashi.auth.repository.UserRepository;
import com.sgkrashi.auth.security.JwtTokenProvider;
import com.sgkrashi.auth.service.AuthResult;
import com.sgkrashi.auth.service.AuthService;
import com.sgkrashi.auth.service.GoogleAuthService;
import com.sgkrashi.common.exception.DuplicateResourceException;
import com.sgkrashi.common.exception.InvalidTokenException;
import com.sgkrashi.notification.entity.Notification;
import com.sgkrashi.notification.sender.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final long REFRESH_TOKEN_TTL_DAYS = 7;
    private static final String CUSTOMER_ROLE = "CUSTOMER";
    private static final long OTP_TTL_MINUTES = 10;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final long OTP_RESEND_COOLDOWN_SECONDS = 60;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final List<NotificationSender> notificationSenders;
    private final GoogleAuthService googleAuthService;
    private final String frontendUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RefreshTokenRepository refreshTokenRepository,
            PendingRegistrationRepository pendingRegistrationRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            UserMapper userMapper,
            List<NotificationSender> notificationSenders,
            GoogleAuthService googleAuthService,
            @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userMapper = userMapper;
        this.notificationSenders = notificationSenders;
        this.googleAuthService = googleAuthService;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Deliberately does not create a {@code User} row here. Rejects an
     * already-registered email up front (a clear 409, same as before) so an
     * OTP is never sent for an address that can't actually register —
     * beyond that check, the pending account (name, email, already-hashed
     * password, phone) is upserted into {@code pending_registrations}
     * keyed by email, so a repeat registration attempt for the same
     * still-pending address just gets a fresh OTP rather than a duplicate
     * row. See {@link #verifyOtp} for where the account is actually
     * created — only once the emailed code is submitted correctly, which is
     * what guarantees every account belongs to an email its owner actually
     * controls, not just a validly-shaped string. The password is hashed
     * here (not in verifyOtp) so the plaintext never has to be persisted
     * anywhere, even transiently.
     */
    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        PendingRegistration pending = pendingRegistrationRepository.findByEmail(request.email())
                .orElseGet(PendingRegistration::new);
        pending.setEmail(request.email());
        pending.setName(request.name());
        pending.setPasswordHash(passwordHash);
        pending.setPhone(request.phone());
        pending.setAttemptCount(0);

        String otp = issueOtp(pending);
        pendingRegistrationRepository.save(pending);

        log.info("Registration pending OTP verification");
        sendOtpEmail(request.name(), request.email(), otp);
    }

    /**
     * The only place a self-service {@code User} row actually gets created.
     * Re-checks {@code existsByEmail} even though {@link #register} already
     * did — a concurrent duplicate registration must not create a second
     * account or silently log into an existing one under someone else's
     * current password, so it's rejected outright rather than treated as
     * "already verified, log them in."
     */
    @Override
    @Transactional
    public AuthResult verifyOtp(VerifyOtpRequest request) {
        PendingRegistration pending = pendingRegistrationRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidOtpException("Incorrect or expired code. Please register again."));

        if (pending.getOtpExpiresAt().isBefore(Instant.now())) {
            pendingRegistrationRepository.delete(pending);
            throw new InvalidOtpException("This code has expired. Please register again.");
        }

        if (pending.getAttemptCount() >= MAX_OTP_ATTEMPTS) {
            throw new TooManyOtpAttemptsException("Too many incorrect attempts. Please request a new code.");
        }

        if (!passwordEncoder.matches(request.otp(), pending.getOtpHash())) {
            pending.setAttemptCount(pending.getAttemptCount() + 1);
            pendingRegistrationRepository.save(pending);
            throw new InvalidOtpException("Incorrect code. Please try again.");
        }

        if (userRepository.existsByEmail(pending.getEmail())) {
            pendingRegistrationRepository.delete(pending);
            throw new DuplicateResourceException("This email is already registered — please log in instead");
        }

        Role customerRole = roleRepository.findByName(CUSTOMER_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Required role '" + CUSTOMER_ROLE + "' is missing — check V2__auth_tables.sql seeding"));

        User user = new User();
        user.setName(pending.getName());
        user.setEmail(pending.getEmail());
        user.setPasswordHash(pending.getPasswordHash());
        user.setPhone(pending.getPhone());
        user.setRoles(Set.of(customerRole));
        user = userRepository.save(user);

        pendingRegistrationRepository.delete(pending);

        return issueTokens(user);
    }

    /**
     * Regenerates the OTP for a still-pending registration and re-sends it.
     * Rate-limited by {@link #OTP_RESEND_COOLDOWN_SECONDS} against {@code
     * lastSentAt} — otherwise a user could spam this endpoint into sending
     * unlimited emails to the same address.
     */
    @Override
    @Transactional
    public void resendOtp(ResendOtpRequest request) {
        PendingRegistration pending = pendingRegistrationRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidOtpException(
                        "No pending registration found for this email. Please register again."));

        long secondsSinceLastSend = Duration.between(pending.getLastSentAt(), Instant.now()).getSeconds();
        if (secondsSinceLastSend < OTP_RESEND_COOLDOWN_SECONDS) {
            long waitSeconds = OTP_RESEND_COOLDOWN_SECONDS - secondsSinceLastSend;
            throw new OtpResendCooldownException(
                    "Please wait " + waitSeconds + " seconds before requesting another code.");
        }

        pending.setAttemptCount(0);
        String otp = issueOtp(pending);
        pendingRegistrationRepository.save(pending);

        sendOtpEmail(pending.getName(), pending.getEmail(), otp);
    }

    /** Generates a fresh 6-digit OTP, hashes it onto {@code pending}, and resets its expiry/send-time — does not save. */
    private String issueOtp(PendingRegistration pending) {
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        pending.setOtpHash(passwordEncoder.encode(otp));
        pending.setOtpExpiresAt(Instant.now().plus(OTP_TTL_MINUTES, ChronoUnit.MINUTES));
        pending.setLastSentAt(Instant.now());
        return otp;
    }

    private void sendOtpEmail(String name, String email, String otp) {
        // Transient User, never saved — exists only so NotificationSender's
        // signature (which reads user.getEmail()/getName()) can be satisfied
        // before any real account exists to attach the notification to.
        User pendingUser = new User();
        pendingUser.setName(name);
        pendingUser.setEmail(email);

        Notification transientNotification = new Notification();
        transientNotification.setTitle("Your SG Krashi verification code");
        transientNotification.setMessage(
                "Welcome to SG Krashi! Your verification code is: " + otp
                        + "\n\nEnter this code in the app to activate your account. It expires in "
                        + OTP_TTL_MINUTES + " minutes.\n\nIf you didn't request this, you can safely ignore this email.");

        for (NotificationSender sender : notificationSenders) {
            try {
                sender.send(transientNotification, pendingUser);
            } catch (Exception ex) {
                log.warn("OTP email failed to send via {}: {}",
                        sender.getClass().getSimpleName(), ex.getClass().getSimpleName());
            }
        }
    }

    /**
     * Authenticates a user via Spring Security's {@link AuthenticationManager}. A
     * wrong password and a nonexistent email both surface as the same
     * {@code BadCredentialsException}, so the response never reveals which was wrong.
     */
    @Override
    @Transactional
    public AuthResult login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalStateException(
                        "User authenticated but could not be reloaded: " + request.email()));

        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthResult loginWithGoogle(String idToken) {
        User user = googleAuthService.findOrCreateUser(idToken);
        return issueTokens(user);
    }

    /**
     * Validates the hashed refresh token, revokes it, and issues a new access +
     * refresh token pair. Rotation happens on every call, not just on expiry.
     */
    @Override
    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidTokenException("No refresh token was supplied");
        }

        String tokenHash = hash(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired session, please log in again"));

        if (!existing.isValid()) {
            throw new InvalidTokenException("Invalid or expired session, please log in again");
        }

        existing.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existing);

        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired session, please log in again"));

        return issueTokens(user);
    }

    /**
     * Revokes the given refresh token if it exists. Always succeeds from the
     * caller's point of view — logging out with an already-expired or absent
     * cookie is not an error.
     */
    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String tokenHash = hash(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    /**
     * Generates a reset link and emails it directly via every registered
     * {@link NotificationSender} — deliberately NOT routed through {@code
     * NotificationService.notify()} like Order/Booking/Refund notifications
     * are: that path persists a {@code Notification} row shown in-app via
     * the notification bell, and a live, usable password-reset token has no
     * business sitting in a general-purpose table a user can browse back to
     * later (nor should it outlive the token's own short expiry). The
     * transient {@link Notification} below exists only to satisfy {@code
     * NotificationSender.send}'s signature — it's never persisted. Each
     * sender is isolated the same way {@code NotificationServiceImpl.notify}
     * isolates its senders: a broken SMTP connection must not surface as a
     * 500 here, since that would reveal whether the email was registered.
     * The link and the address are deliberately never logged: the token is a
     * live credential for 30 minutes and server logs are retained by the host.
     * Only the numeric user id and the failure class are logged.
     */
    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            String resetToken = jwtTokenProvider.generatePasswordResetToken(user.getEmail());
            String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
            log.info("Password reset requested for userId={}", user.getId());

            Notification transientNotification = new Notification();
            transientNotification.setTitle("Reset Your SG Krashi Password");
            transientNotification.setMessage(
                    "We received a request to reset your password. Click the link below to choose a new one:\n\n"
                            + resetLink
                            + "\n\nIf you didn't request this, you can safely ignore this email.");

            for (NotificationSender sender : notificationSenders) {
                try {
                    sender.send(transientNotification, user);
                } catch (Exception ex) {
                    log.warn("Password reset email failed to send via {} for userId={}: {}",
                            sender.getClass().getSimpleName(), user.getId(), ex.getClass().getSimpleName());
                }
            }
        });
    }

    /**
     * Applies a new password after validating the reset token, then revokes every
     * outstanding refresh token for the account — a password reset should end all
     * existing sessions, not just the one that requested it.
     */
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email;
        try {
            email = jwtTokenProvider.getResetTokenEmail(request.token());
        } catch (Exception ex) {
            // Catches more than JwtException/IllegalArgumentException on
            // purpose — a garbage, non-JWT-shaped token (found live: a
            // plain string like "bad" with no dots) throws a different
            // exception type further down in the JJWT parsing chain than
            // the two types this used to catch, which surfaced as a raw 500
            // instead of a clean "invalid token" response. Any parse
            // failure here means the same thing to the caller regardless of
            // its exact type.
            throw new InvalidTokenException("Invalid or expired reset token");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired reset token"));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        refreshTokenRepository.findAll().stream()
                .filter(token -> token.getUserId().equals(user.getId()) && token.isValid())
                .forEach(token -> {
                    token.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(token);
                });
    }

    private AuthResult issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);

        String rawRefreshToken = generateOpaqueToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(hash(rawRefreshToken));
        refreshToken.setExpiresAt(Instant.now().plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS));
        refreshTokenRepository.save(refreshToken);

        AuthResponse response = new AuthResponse(accessToken, userMapper.toSummary(user));
        return new AuthResult(response, rawRefreshToken);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
