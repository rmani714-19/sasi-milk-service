package com.sasimilkservice.region.repository;

import com.sasimilkservice.region.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);
}
