package com.sgkrashi.auth.service.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.sgkrashi.auth.dto.request.ForgotPasswordRequest;
import com.sgkrashi.auth.dto.request.RegisterRequest;
import com.sgkrashi.auth.dto.request.ResendOtpRequest;
import com.sgkrashi.auth.entity.PendingRegistration;
import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.repository.PendingRegistrationRepository;
import com.sgkrashi.auth.repository.RefreshTokenRepository;
import com.sgkrashi.auth.repository.RoleRepository;
import com.sgkrashi.auth.repository.UserRepository;
import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.auth.security.CustomUserDetailsService;
import com.sgkrashi.auth.security.JwtTokenProvider;
import com.sgkrashi.notification.entity.Notification;
import com.sgkrashi.notification.sender.NotificationSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Server logs are retained by the host, so a password-reset token (a live credential for 30 minutes), a sign-up OTP
 * or a user's email address must never reach one. These tests run the real {@link AuthServiceImpl} flows, capture
 * every log event from every logger (message, arguments and any exception text), and fail if the secret or the
 * address appears. The email itself must still be handed to the sender intact.
 */
class AuthServiceLogLeakTest {

    private static final String EMAIL = "farmer.leaktest@example.com";
    private static final String FRONTEND_URL = "https://app.example.test";
    private static final Pattern OTP = Pattern.compile("code is: (\\d{6})");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PendingRegistrationRepository pendingRepository = mock(PendingRegistrationRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider("0123456789-0123456789-0123456789-0123456789");
    private final List<Notification> sent = new ArrayList<>();

    private ListAppender<ILoggingEvent> appender;
    private Logger root;
    private Level originalLevel;

    @BeforeEach
    void captureAllLogging() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        originalLevel = root.getLevel();
        root.setLevel(Level.ALL);
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
    }

    @AfterEach
    void restoreLogging() {
        root.detachAppender(appender);
        root.setLevel(originalLevel);
        SecurityContextHolder.clearContext();
    }

    private AuthServiceImpl service(NotificationSender sender) {
        return new AuthServiceImpl(userRepository, mock(RoleRepository.class), mock(RefreshTokenRepository.class),
                pendingRepository, passwordEncoder, mock(AuthenticationManager.class), jwtTokenProvider, null,
                List.of(sender), null, FRONTEND_URL);
    }

    private NotificationSender recordingSender() {
        return (notification, user) -> sent.add(notification);
    }

    /** Behaves like an SMTP/HTTP mail failure: the exception text echoes the recipient. */
    private NotificationSender failingSender() {
        return (notification, user) -> {
            throw new IllegalStateException("550 5.1.1 <" + user.getEmail() + ">: Recipient address rejected");
        };
    }

    private String everythingLogged() {
        StringBuilder all = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            all.append(event.getFormattedMessage()).append('\n');
            for (Object arg : event.getArgumentArray() == null ? new Object[0] : event.getArgumentArray()) {
                all.append(arg).append('\n');
            }
            for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
                all.append(t.getClassName()).append(": ").append(t.getMessage()).append('\n');
            }
        }
        return all.toString();
    }

    private User existingUser() {
        User user = new User();
        user.setName("Test Farmer");
        user.setEmail(EMAIL);
        ReflectionTestUtils.setField(user, "id", 42L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        return user;
    }

    private String resetTokenFrom(Notification notification) {
        String message = notification.getMessage();
        int start = message.indexOf("/reset-password?token=");
        assertTrue(start >= 0, "the email must still carry the reset link");
        String afterToken = message.substring(start + "/reset-password?token=".length());
        return afterToken.substring(0, afterToken.indexOf('\n'));
    }

    private void assertNoLeak(String logged, String... secrets) {
        for (String secret : secrets) {
            assertFalse(logged.contains(secret), "log output must not contain a secret/identifier");
        }
        assertFalse(logged.contains("reset-password"), "log output must not contain a reset link");
        assertFalse(logged.toLowerCase().contains("token="), "log output must not contain a token query");
        assertFalse(logged.contains(EMAIL), "log output must not contain the email address");
        assertFalse(logged.contains("@example.com"), "log output must not contain any email address");
    }

    @Test
    void theLogCaptureActuallySeesLogging() {
        LoggerFactory.getLogger("probe").info("hello");
        assertTrue(everythingLogged().contains("hello"), "otherwise the other tests could pass for the wrong reason");
    }

    @Test
    void forgotPasswordNeverLogsTheResetTokenOrTheEmailButStillSendsTheLink() {
        existingUser();

        service(recordingSender()).forgotPassword(new ForgotPasswordRequest(EMAIL));

        assertTrue(sent.size() == 1, "the reset email must still be sent");
        String token = resetTokenFrom(sent.get(0));
        assertFalse(token.isBlank());
        assertTrue(sent.get(0).getMessage().contains(FRONTEND_URL + "/reset-password?token=" + token));
        assertTrue(everythingLogged().contains("userId=42"), "a non-identifying line is still logged");
        assertNoLeak(everythingLogged(), token);
    }

    @Test
    void forgotPasswordSenderFailureDoesNotLogTheEmailOrTheToken() {
        existingUser();
        // The sender fails after the token exists; capture it through a sender that records then fails.
        List<Notification> seen = new ArrayList<>();
        NotificationSender recordThenFail = (notification, user) -> {
            seen.add(notification);
            throw new IllegalStateException("550 5.1.1 <" + user.getEmail() + ">: Recipient address rejected");
        };

        service(recordThenFail).forgotPassword(new ForgotPasswordRequest(EMAIL));

        String token = resetTokenFrom(seen.get(0));
        String logged = everythingLogged();
        assertTrue(logged.contains("IllegalStateException"), "the failure is still logged by class");
        assertNoLeak(logged, token);
    }

    @Test
    void forgotPasswordForAnUnknownEmailLogsNothingIdentifying() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service(recordingSender()).forgotPassword(new ForgotPasswordRequest("nobody@example.com"));

        assertTrue(sent.isEmpty());
        assertFalse(everythingLogged().contains("nobody@example.com"));
    }

    @Test
    void registrationNeverLogsTheEmailOrTheOtp() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(pendingRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        service(recordingSender()).register(new RegisterRequest("Test Farmer", EMAIL, "a-long-password", "9999999999"));

        Matcher otp = OTP.matcher(sent.get(0).getMessage());
        assertTrue(otp.find(), "the OTP email must still be sent");
        assertTrue(everythingLogged().contains("Registration pending OTP verification"));
        assertNoLeak(everythingLogged(), otp.group(1), "9999999999");
    }

    @Test
    void registrationOtpSenderFailureDoesNotLogTheEmail() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(pendingRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        service(failingSender()).register(new RegisterRequest("Test Farmer", EMAIL, "a-long-password", null));

        assertTrue(everythingLogged().contains("OTP email failed to send"));
        assertNoLeak(everythingLogged());
    }

    @Test
    void resendOtpDoesNotLogTheEmail() {
        PendingRegistration pending = new PendingRegistration();
        pending.setEmail(EMAIL);
        pending.setName("Test Farmer");
        pending.setLastSentAt(java.time.Instant.now().minusSeconds(3600));
        when(pendingRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));

        service(failingSender()).resendOtp(new ResendOtpRequest(EMAIL));

        assertNoLeak(everythingLogged());
    }

    @Test
    void lookupFailuresDoNotCarryTheEmailInTheirMessage() {
        // These messages reach the log through the global exception handler.
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        UsernameNotFoundException notFound = org.junit.jupiter.api.Assertions.assertThrows(
                UsernameNotFoundException.class, () -> new CustomUserDetailsService(userRepository).loadUserByUsername(EMAIL));
        assertNotNull(notFound.getMessage());
        assertFalse(notFound.getMessage().contains(EMAIL));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(EMAIL, null, List.of()));
        IllegalStateException missing = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> new CurrentUserProvider(userRepository).getCurrentUser());
        assertFalse(missing.getMessage().contains(EMAIL));
    }
}
