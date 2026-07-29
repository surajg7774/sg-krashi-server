package com.sgkrashi.customer.mapper;

import com.sgkrashi.auth.entity.Role;
import com.sgkrashi.auth.entity.User;
import com.sgkrashi.customer.dto.response.CustomerProfileResponse;
import org.springframework.stereotype.Component;

/**
 * Converts {@link User} to the outbound profile DTO. Like {@code UserMapper},
 * this is the only path by which user data leaves the service layer —
 * {@link User#getPasswordHash()} is structurally impossible to reach through
 * {@link CustomerProfileResponse}.
 */
@Component
public class CustomerProfileMapper {

    public CustomerProfileResponse toResponse(User user) {
        var roleNames = user.getRoles().stream().map(Role::getName).toList();
        return new CustomerProfileResponse(
                user.getId(), user.getName(), user.getEmail(), user.getPhone(), roleNames);
    }
}
