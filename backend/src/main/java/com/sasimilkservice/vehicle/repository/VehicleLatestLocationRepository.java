package com.sasimilkservice.vehicle.repository;

import com.sasimilkservice.vehicle.domain.VehicleLatestLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehicleLatestLocationRepository extends JpaRepository<VehicleLatestLocation, Long> {

    Optional<VehicleLatestLocation> findByVehicleId(Long vehicleId);
}
