package com.sgkrashi.auth.security;

import com.sgkrashi.auth.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the {@code Authorization: Bearer <token>} header on every request. An
 * invalid or absent token simply leaves the context unauthenticated;
 * downstream authorization rules decide whether that's acceptable for the
 * requested endpoint.
 *
 * <p><b>Module 14 addition:</b> this used to populate the security context
 * directly from the token's claims with no database lookup at all ("no
 * database lookup on the hot path"). That meant a user deactivated by an
 * Admin kept working with any access token issued before deactivation, for
 * up to that token's full 15-minute lifetime — Module 14's first real
 * exercise of deactivation (Section 4.1's login-blocking requirement) caught
 * this directly: deactivating a test user did not, in fact, invalidate their
 * already-issued token. A stateless JWT can't support instant revocation
 * without giving something up; the deliberate tradeoff made here is one
 * {@code UserRepository} lookup per authenticated request in exchange for
 * deactivation actually taking effect immediately, not up to 15 minutes
 * later. If this lookup ever shows up as a hot-path cost at real scale, a
 * short-TTL cache keyed by user id would remove most of it without giving
 * back the immediacy guarantee.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(AUTH_HEADER);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtTokenProvider.parseClaims(token);
                String email = claims.get("email", String.class);

                boolean stillActive = userRepository.findByEmail(email)
                        .map(user -> user.isActive())
                        .orElse(false);

                if (stillActive) {
                    var authorities = jwtTokenProvider.getRoles(claims).stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .toList();

                    Authentication authentication = new UsernamePasswordAuthenticationToken(email, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    SecurityContextHolder.clearContext();
                }
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
