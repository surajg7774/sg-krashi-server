package com.sgkrashi.auth.security;

import com.sgkrashi.auth.entity.Role;
import com.sgkrashi.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Issues and validates short-lived HS256 access tokens. The signing secret has
 * no fallback default (see {@code application.yml}) — the application refuses
 * to start rather than silently signing tokens with a known/leaked key.
 */
@Component
public class JwtTokenProvider {

    private static final long ACCESS_TOKEN_TTL_MINUTES = 15;
    private static final long RESET_TOKEN_TTL_MINUTES = 30;
    // Shared by both single-purpose token types below (reset and email
    // verification) — same claim key, different value, so either token is
    // rejected outright if handed to the wrong verb (getResetTokenEmail
    // would reject an email-verification token here, and vice versa).
    private static final String PURPOSE_CLAIM = "purpose";
    private static final String RESET_TOKEN_PURPOSE_VALUE = "password-reset";

    // Longer-lived than the reset token on purpose: a password reset is
    // usually acted on right away (the user is mid-"I'm locked out"), but
    // someone registering may not check their inbox for a while — 24 hours
    // gives real headroom before they'd need to just submit the form again.
    private static final long EMAIL_VERIFICATION_TOKEN_TTL_MINUTES = 24 * 60;
    private static final String EMAIL_VERIFICATION_PURPOSE_VALUE = "email-verification";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_PASSWORD_HASH = "passwordHash";
    private static final String CLAIM_PHONE = "phone";

    private final SecretKey signingKey;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        List<String> roleNames = user.getRoles().stream().map(Role::getName).toList();

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("roles", roleNames)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ACCESS_TOKEN_TTL_MINUTES * 60)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and validates a token's signature and expiry.
     *
     * @throws JwtException if the token is malformed, expired, or has an invalid signature
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        return (List<String>) claims.get("roles", List.class);
    }

    /**
     * Generates a short-lived, single-purpose token for the forgot-password flow.
     * Signed with the same platform secret but tagged with a {@code purpose} claim
     * so it can never be accepted as an access token, or vice versa.
     */
    public String generatePasswordResetToken(String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim(PURPOSE_CLAIM, RESET_TOKEN_PURPOSE_VALUE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(RESET_TOKEN_TTL_MINUTES * 60)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * @throws JwtException if the token is malformed, expired, or not a password-reset token
     */
    public String getResetTokenEmail(String token) {
        Claims claims = parseClaims(token);
        if (!RESET_TOKEN_PURPOSE_VALUE.equals(claims.get(PURPOSE_CLAIM, String.class))) {
            throw new JwtException("Token is not a password-reset token");
        }
        return claims.getSubject();
    }

    /**
     * Generates the token a "verify your email" link carries — the pending
     * account's data lives entirely in the token's claims, not in any
     * database row, which is what makes the whole flow work: no {@code
     * User} exists at all until this token comes back validated (see {@code
     * AuthServiceImpl#verifyEmail}). {@code passwordHash} is the already-bcrypt-hashed
     * value, never the plaintext password — safe to embed in a signed token
     * the same way it's safe to store in the database, since it's a one-way
     * hash either way.
     */
    public String generateEmailVerificationToken(String name, String email, String passwordHash, String phone) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(email)
                .claim(PURPOSE_CLAIM, EMAIL_VERIFICATION_PURPOSE_VALUE)
                .claim(CLAIM_NAME, name)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_PASSWORD_HASH, passwordHash)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(EMAIL_VERIFICATION_TOKEN_TTL_MINUTES * 60)));
        if (phone != null) {
            builder.claim(CLAIM_PHONE, phone);
        }
        return builder.signWith(signingKey).compact();
    }

    /** Everything {@code AuthServiceImpl#verifyEmail} needs to actually create the account — extracted from the token's claims, never trusted from the request otherwise. */
    public record PendingRegistration(String name, String email, String passwordHash, String phone) {
    }

    /**
     * @throws JwtException if the token is malformed, expired, or not an email-verification token
     */
    public PendingRegistration getPendingRegistration(String token) {
        Claims claims = parseClaims(token);
        if (!EMAIL_VERIFICATION_PURPOSE_VALUE.equals(claims.get(PURPOSE_CLAIM, String.class))) {
            throw new JwtException("Token is not an email-verification token");
        }
        return new PendingRegistration(
                claims.get(CLAIM_NAME, String.class),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_PASSWORD_HASH, String.class),
                claims.get(CLAIM_PHONE, String.class));
    }
}
