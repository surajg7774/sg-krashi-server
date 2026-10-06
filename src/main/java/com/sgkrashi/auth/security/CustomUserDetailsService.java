package com.sgkrashi.auth.security;

import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.exception.GoogleOnlyAccountException;
import com.sgkrashi.auth.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads a {@link UserDetails} by email for Spring Security's authentication provider.
 * A missing user surfaces as {@link UsernameNotFoundException}, which
 * {@link org.springframework.security.authentication.dao.DaoAuthenticationProvider}
 * converts into the same {@code BadCredentialsException} as a wrong password,
 * so a login failure never reveals whether the email or the password was wrong.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // A Google-only account (see User's Javadoc) has passwordHash =
        // null. Spring Security's own User.builder().password(null) throws
        // a raw IllegalArgumentException ("password cannot be null") that
        // would otherwise surface as an opaque 500 — confirmed by reading
        // that builder's source, not guessed. Checked explicitly here, with
        // its own exception, so the caller gets a clean, actionable message
        // instead.
        if (user.getPasswordHash() == null) {
            throw new GoogleOnlyAccountException("This account uses Google Sign-In. Please log in with Google.");
        }

        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                .toList();

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(!user.isActive())
                .authorities(authorities)
                .build();
    }
}
