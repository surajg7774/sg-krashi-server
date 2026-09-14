package com.sgkrashi.advisory.repository;

import com.sgkrashi.advisory.entity.FarmerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FarmerProfileRepository extends JpaRepository<FarmerProfile, Long> {

    Optional<FarmerProfile> findByUserId(Long userId);

    List<FarmerProfile> findByWeatherAdvisoryOptInTrue();
}
