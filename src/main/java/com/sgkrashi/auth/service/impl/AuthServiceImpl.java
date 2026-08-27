package com.sgkrashi.auth.service.impl;

import com.sgkrashi.auth.dto.request.ForgotPasswordRequest;
import com.sgkrashi.auth.dto.request.LoginRequest;
import com.sgkrashi.auth.dto.request.RegisterRequest;
import com.sgkrashi.auth.dto.request.ResetPasswordRequest;
import com.sgkrashi.auth.dto.request.VerifyEmailRequest;
import com.sgkrashi.auth.dto.response.AuthResponse;
import com.sgkrashi.auth.entity.RefreshToken;
import com.sgkrashi.auth.entity.Role;
import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.mapper.UserMapper;
import com.sgkrashi.auth.repository.RefreshTokenRepository;
import com.sgkrashi.auth.repository.RoleRepository;
import com.sgkrashi.auth.repository.UserRepository;
import com.sgkrashi.auth.security.JwtTokenProvider;
import com.sgkrashi.auth.service.AuthResult;
import com.sgkrashi.auth.service.AuthService;
import com.sgkrashi.common.exception.DuplicateResourceException;
import com.sgkrashi.common.exception.InvalidTokenException;
import com.sgkrashi.notification.entity.Notification;
import com.sgkrashi.notification.sender.NotificationSender;
import io.jsonwebtoken.JwtException;
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

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final List<NotificationSender> notificationSenders;
    private final String frontendUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            UserMapper userMapper,
            List<NotificationSender> notificationSenders,
            @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userMapper = userMapper;
        this.notificationSenders = notificationSenders;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Deliberately does not create a {@code User} row here. Rejects an
     * already-registered email up front (a clear 409, same as before) so a
     * verification email is never sent for an address that can't actually
     * register — but beyond that check, nothing is persisted: the pending
     * account (name, email, already-hashed password, phone) travels entirely
     * inside the signed verification token emailed below. See {@link
     * #verifyEmail} for where the account is actually created — only once
     * that link is clicked, which is what guarantees every account belongs
     * to an email its owner actually controls, not just a validly-shaped
     * string. The password is hashed here (not in verifyEmail) so the
     * plaintext never has to travel through or be embedded in anything —
     * the token only ever carries the one-way hash, exactly what would be
     * stored in the database anyway.
     */
    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        String verificationToken = jwtTokenProvider.generateEmailVerificationToken(
                request.name(), request.email(), passwordHash, request.phone());
        String verificationLink = frontendUrl + "/verify-email?token=" + verificationToken;
        log.info("Registration pending verification for {}. Verification link: {}", request.email(), verificationLink);

        // Transient User, never saved — exists only so NotificationSender's
        // signature (which reads user.getEmail()/getName()) can be satisfied
        // before any real account exists to attach the notification to.
        User pendingUser = new User();
        pendingUser.setName(request.name());
        pendingUser.setEmail(request.email());

        Notification transientNotification = new Notification();
        transientNotification.setTitle("Verify Your SG Krashi Account");
        transientNotification.setMessage(
                "Welcome to SG Krashi! Please verify your email address to activate your account:\n\n"
                        + verificationLink
                        + "\n\nThis link expires in 24 hours. If you didn't create this account, "
                        + "you can safely ignore this email.");

        for (NotificationSender sender : notificationSenders) {
            try {
                sender.send(transientNotification, pendingUser);
            } catch (Exception ex) {
                log.warn("Verification email failed to send via {} for {}: {}",
                        sender.getClass().getSimpleName(), request.email(), ex.getMessage());
            }
        }
    }

    /**
     * The only place a self-service {@code User} row actually gets created.
     * Re-checks {@code existsByEmail} even though {@link #register} already
     * did — a stale/duplicate verification link (the same email registered
     * twice, or the link clicked more than once) must not create a second
     * account or silently log into an existing one under someone else's
     * current password, so it's rejected outright rather than treated as
     * "already verified, log them in."
     */
    @Override
    @Transactional
    public AuthResult verifyEmail(VerifyEmailRequest request) {
        JwtTokenProvider.PendingRegistration pending;
        try {
            pending = jwtTokenProvider.getPendingRegistration(request.token());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Invalid or expired verification link");
        }

        if (userRepository.existsByEmail(pending.email())) {
            throw new DuplicateResourceException("This email is already registered — please log in instead");
        }

        Role customerRole = roleRepository.findByName(CUSTOMER_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Required role '" + CUSTOMER_ROLE + "' is missing — check V2__auth_tables.sql seeding"));

        User user = new User();
        user.setName(pending.name());
        user.setEmail(pending.email());
        user.setPasswordHash(pending.passwordHash());
        user.setPhone(pending.phone());
        user.setRoles(Set.of(customerRole));
        user = userRepository.save(user);

        return issueTokens(user);
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
     * Still logs the link too — useful in local dev without needing a real
     * inbox, and this is a server log, not anything user-facing.
     */
    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            String resetToken = jwtTokenProvider.generatePasswordResetToken(user.getEmail());
            String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
            log.info("Password reset requested for {}. Reset link: {}", user.getEmail(), resetLink);

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
                    log.warn("Password reset email failed to send via {} for {}: {}",
                            sender.getClass().getSimpleName(), user.getEmail(), ex.getMessage());
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
        } catch (JwtException | IllegalArgumentException ex) {
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
