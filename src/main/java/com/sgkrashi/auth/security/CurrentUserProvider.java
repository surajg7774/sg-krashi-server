package com.sgkrashi.auth.security;

import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link User} for the currently authenticated request from the
 * {@link SecurityContextHolder} — the JWT principal is the user's email (see
 * {@link JwtAuthenticationFilter}), never a client-suppliable ID. Every endpoint
 * that must act on "my own data" (customer profile, addresses, etc.) should go
 * through this rather than trusting any ID passed in the URL or body.
 */
@Component
public class CurrentUserProvider {

    private static final String ANONYMOUS_PRINCIPAL = "anonymousUser";

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    /**
     * Same resolution as {@link #getCurrentUserId()}, but for endpoints that
     * are reachable by both Guests and logged-in Customers (e.g. {@code POST
     * /api/v1/inquiries}) — returns {@code null} instead of throwing when
     * there is no authenticated principal on the request.
     */
    public Long getCurrentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || ANONYMOUS_PRINCIPAL.equals(authentication.getName())) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName()).map(User::getId).orElse(null);
    }
}
