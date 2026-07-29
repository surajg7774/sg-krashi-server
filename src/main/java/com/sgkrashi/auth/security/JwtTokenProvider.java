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
    private static final String RESET_TOKEN_PURPOSE_CLAIM = "purpose";
    private static final String RESET_TOKEN_PURPOSE_VALUE = "password-reset";

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
                .claim(RESET_TOKEN_PURPOSE_CLAIM, RESET_TOKEN_PURPOSE_VALUE)
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
        if (!RESET_TOKEN_PURPOSE_VALUE.equals(claims.get(RESET_TOKEN_PURPOSE_CLAIM, String.class))) {
            throw new JwtException("Token is not a password-reset token");
        }
        return claims.getSubject();
    }
}
