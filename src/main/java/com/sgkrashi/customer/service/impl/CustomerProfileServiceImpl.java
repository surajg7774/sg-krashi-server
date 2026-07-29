package com.sgkrashi.customer.service.impl;

import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.repository.UserRepository;
import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.customer.dto.request.ChangePasswordRequest;
import com.sgkrashi.customer.dto.request.UpdateProfileRequest;
import com.sgkrashi.customer.dto.response.CustomerProfileResponse;
import com.sgkrashi.customer.mapper.CustomerProfileMapper;
import com.sgkrashi.customer.service.CustomerProfileService;
import com.sgkrashi.common.exception.ValidationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerProfileServiceImpl implements CustomerProfileService {

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final CustomerProfileMapper customerProfileMapper;
    private final PasswordEncoder passwordEncoder;

    public CustomerProfileServiceImpl(
            UserRepository userRepository,
            CurrentUserProvider currentUserProvider,
            CustomerProfileMapper customerProfileMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.customerProfileMapper = customerProfileMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Returns the authenticated user's own profile — resolved from the JWT
     * principal, never a client-supplied ID.
     */
    @Override
    public CustomerProfileResponse getProfile() {
        return customerProfileMapper.toResponse(currentUserProvider.getCurrentUser());
    }

    /**
     * Updates name/phone for the authenticated user. Email is intentionally
     * excluded — changing it is out of scope for this module.
     */
    @Override
    @Transactional
    public CustomerProfileResponse updateProfile(UpdateProfileRequest request) {
        User user = currentUserProvider.getCurrentUser();
        user.setName(request.name());
        user.setPhone(request.phone());
        User saved = userRepository.save(user);
        return customerProfileMapper.toResponse(saved);
    }

    /**
     * Changes the authenticated user's password after verifying the current one.
     * Does not revoke refresh tokens or otherwise touch the current session —
     * only a future login would need the new password.
     */
    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = currentUserProvider.getCurrentUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ValidationException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }
}
