package com.sgkrashi.advisory.service.impl;

import com.sgkrashi.advisory.dto.FarmerProfileRequest;
import com.sgkrashi.advisory.dto.FarmerProfileResponse;
import com.sgkrashi.advisory.entity.FarmerProfile;
import com.sgkrashi.advisory.repository.FarmerProfileRepository;
import com.sgkrashi.advisory.service.FarmerProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class FarmerProfileServiceImpl implements FarmerProfileService {

    private final FarmerProfileRepository farmerProfileRepository;

    public FarmerProfileServiceImpl(FarmerProfileRepository farmerProfileRepository) {
        this.farmerProfileRepository = farmerProfileRepository;
    }

    @Override
    public FarmerProfileResponse getOwnProfile(Long userId) {
        return farmerProfileRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElse(new FarmerProfileResponse(null, null, null, false));
    }

    @Override
    @Transactional
    public FarmerProfileResponse updateOwnProfile(Long userId, FarmerProfileRequest request) {
        FarmerProfile profile = farmerProfileRepository.findByUserId(userId).orElseGet(() -> {
            FarmerProfile created = new FarmerProfile();
            created.setUserId(userId);
            return created;
        });
        profile.setLatitude(request.latitude());
        profile.setLongitude(request.longitude());
        profile.setPlaceName(request.placeName());
        profile.setWeatherAdvisoryOptIn(request.weatherAdvisoryOptIn());
        return toResponse(farmerProfileRepository.save(profile));
    }

    private FarmerProfileResponse toResponse(FarmerProfile profile) {
        BigDecimal lat = profile.getLatitude();
        BigDecimal lon = profile.getLongitude();
        return new FarmerProfileResponse(lat, lon, profile.getPlaceName(), profile.isWeatherAdvisoryOptIn());
    }
}
