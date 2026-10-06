package com.sgkrashi.auth.service.impl;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.sgkrashi.auth.entity.Role;
import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.exception.GoogleSignInNotConfiguredException;
import com.sgkrashi.auth.exception.InvalidGoogleTokenException;
import com.sgkrashi.auth.repository.RoleRepository;
import com.sgkrashi.auth.repository.UserRepository;
import com.sgkrashi.auth.service.GoogleAuthService;
import com.sgkrashi.notification.entity.Notification;
import com.sgkrashi.notification.sender.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Verifies a Google ID token (audience = the Web OAuth client ID — see this
 * class's constructor Javadoc for why that one specifically, not the
 * Android/iOS client IDs) and resolves it to a {@code User}. The
 * find-or-create precedence is:
 *
 * <ol>
 *   <li>An existing account already linked by {@code googleId} — the common
 *       case for a returning Google user.</li>
 *   <li>An existing password-based account whose email matches — auto-linked
 *       (this {@code googleId} is saved onto it) rather than rejected, since
 *       Google's own {@code email_verified} guarantee makes this safe: the
 *       token proves the signer controls that inbox, the same guarantee this
 *       app's own email-verification-link registration flow relies on.</li>
 *   <li>Neither — a brand new account, {@code passwordHash} left {@code
 *       null} (see {@code User}'s Javadoc).</li>
 * </ol>
 */
@Service
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthServiceImpl.class);
    private static final String CUSTOMER_ROLE = "CUSTOMER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final List<NotificationSender> notificationSenders;
    private final GoogleIdTokenVerifier verifier;
    private final String webClientId;

    public GoogleAuthServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            List<NotificationSender> notificationSenders,
            // Empty default (not a required property) on purpose — this
            // must not crash the whole app's boot before a real Google Cloud
            // project exists, the same lesson already learned once this
            // session with FCM_ENABLED. isConfigured() below is the actual
            // gate; an empty audience list here is simply never reached with
            // a real token, since every call site checks isConfigured()
            // first.
            @Value("${app.google.web-client-id:}") String webClientId
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.notificationSenders = notificationSenders;
        this.webClientId = webClientId;
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                // The Web client ID, always — even for a token obtained by
                // the Android/iOS app. The mobile Google Sign-In library is
                // configured with the Web client ID as its "server client
                // ID" / webClientId specifically so the ID token it returns
                // carries THIS audience, verifiable by a backend that only
                // knows the Web client ID. Verifying against the Android/iOS
                // client ID instead would require the backend to also know
                // those (a second, needless credential to manage) and gains
                // nothing.
                .setAudience(Collections.singletonList(webClientId))
                .build();
    }

    private boolean isConfigured() {
        return webClientId != null && !webClientId.isBlank();
    }

    @Override
    @Transactional
    public User findOrCreateUser(String idToken) {
        if (!isConfigured()) {
            throw new GoogleSignInNotConfiguredException("Google Sign-In is not configured yet on this server.");
        }

        GoogleIdToken.Payload payload = verify(idToken);
        String googleId = payload.getSubject();
        String email = payload.getEmail();
        Boolean emailVerified = payload.getEmailVerified();
        String name = (String) payload.get("name");

        if (email == null || !Boolean.TRUE.equals(emailVerified)) {
            // Google only sets this false for a handful of legacy/unverified
            // account types — genuinely rare, but auto-linking (or creating
            // an account at all) on an unverified email would defeat the
            // entire safety argument for auto-linking in the first place.
            throw new InvalidGoogleTokenException("Google did not confirm this account's email address.");
        }

        return userRepository.findByGoogleId(googleId)
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(existing -> linkGoogleId(existing, googleId))
                        .orElseGet(() -> createGoogleUser(googleId, email, name)));
    }

    @Override
    public String verifyAndGetSubject(String idToken) {
        if (!isConfigured()) {
            throw new GoogleSignInNotConfiguredException("Google Sign-In is not configured yet on this server.");
        }
        return verify(idToken).getSubject();
    }

    private GoogleIdToken.Payload verify(String idToken) {
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new InvalidGoogleTokenException("Google sign-in token is invalid or expired.");
            }
            return token.getPayload();
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            log.warn("Google ID token verification failed: {}", ex.getClass().getSimpleName());
            throw new InvalidGoogleTokenException("Google sign-in token could not be verified.", ex);
        } catch (java.io.IOException ex) {
            log.warn("Google ID token verification failed (network): {}", ex.getClass().getSimpleName());
            throw new InvalidGoogleTokenException("Could not reach Google to verify the sign-in token.", ex);
        }
    }

    private User linkGoogleId(User existing, String googleId) {
        existing.setGoogleId(googleId);
        return userRepository.save(existing);
    }

    private User createGoogleUser(String googleId, String email, String name) {
        Role customerRole = roleRepository.findByName(CUSTOMER_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Required role '" + CUSTOMER_ROLE + "' is missing — check V2__auth_tables.sql seeding"));

        User user = new User();
        user.setName(name != null ? name : email);
        user.setEmail(email);
        user.setGoogleId(googleId);
        user.setPasswordHash(null);
        user.setRoles(Set.of(customerRole));
        User saved = userRepository.save(user);
        sendWelcomeEmail(saved);
        return saved;
    }

    /**
     * Only reached from {@link #createGoogleUser} — a genuinely new account,
     * never {@link #linkGoogleId}'s existing-password-account case, which
     * already has whatever welcome email it got when it first registered.
     * Password registration gets its "welcome" wording folded into the OTP
     * email itself (see {@code AuthServiceImpl#sendOtpEmail}); Google
     * signup skips that OTP step entirely (Google already verified the
     * email), so without this it would get no welcome message of any kind.
     */
    private void sendWelcomeEmail(User user) {
        Notification transientNotification = new Notification();
        transientNotification.setTitle("Welcome to SG Krashi!");
        transientNotification.setMessage(
                "Welcome to SG Krashi, " + user.getName() + "! Your account is ready to go — "
                        + "explore the Product Store, Crop Marketplace, AI Crop Doctor, Mandi Prices and more.");

        for (NotificationSender sender : notificationSenders) {
            try {
                sender.send(transientNotification, user);
            } catch (Exception ex) {
                log.warn("Welcome email failed to send via {} for userId={}: {}",
                        sender.getClass().getSimpleName(), user.getId(), ex.getClass().getSimpleName());
            }
        }
    }
}
