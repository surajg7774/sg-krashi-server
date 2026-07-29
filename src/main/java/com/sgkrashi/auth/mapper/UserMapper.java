package com.sgkrashi.auth.mapper;

import com.sgkrashi.auth.dto.response.AuthResponse;
import com.sgkrashi.auth.entity.Role;
import com.sgkrashi.auth.entity.User;
import org.springframework.stereotype.Component;

/**
 * Converts {@link User} entities to the outbound user-summary DTO. This is the
 * only path by which user data leaves the service layer — {@link User#getPasswordHash()}
 * is structurally impossible to reach through {@link AuthResponse.UserSummary}.
 */
@Component
public class UserMapper {

    public AuthResponse.UserSummary toSummary(User user) {
        var roleNames = user.getRoles().stream().map(Role::getName).toList();
        return new AuthResponse.UserSummary(user.getId(), user.getName(), user.getEmail(), roleNames);
    }
}
