package com.sgkrashi.advisory.service;

import com.sgkrashi.advisory.dto.FarmerProfileRequest;
import com.sgkrashi.advisory.dto.FarmerProfileResponse;

public interface FarmerProfileService {

    /** Never null — returns an "empty" profile (no location, opted out) for a farmer who hasn't saved settings yet, rather than 404ing. */
    FarmerProfileResponse getOwnProfile(Long userId);

    FarmerProfileResponse updateOwnProfile(Long userId, FarmerProfileRequest request);
}
