package com.sasimilkservice.vehicle.repository;

import com.sasimilkservice.vehicle.domain.VehicleLocation;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleLocationRepository extends JpaRepository<VehicleLocation, Long> {

    Optional<VehicleLocation> findFirstByVehicleIdOrderByRecordedAtDesc(Long vehicleId);

    List<VehicleLocation> findByVehicleIdOrderByRecordedAtDesc(Long vehicleId, Limit limit);
}
