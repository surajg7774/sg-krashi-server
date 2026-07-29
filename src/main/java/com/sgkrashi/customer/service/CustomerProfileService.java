package com.sgkrashi.customer.service;

import com.sgkrashi.customer.dto.request.ChangePasswordRequest;
import com.sgkrashi.customer.dto.request.UpdateProfileRequest;
import com.sgkrashi.customer.dto.response.CustomerProfileResponse;

public interface CustomerProfileService {

    CustomerProfileResponse getProfile();

    CustomerProfileResponse updateProfile(UpdateProfileRequest request);

    /**
     * @throws com.sgkrashi.common.exception.ValidationException if the current password is incorrect
     */
    void changePassword(ChangePasswordRequest request);
}
