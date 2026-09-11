package com.sasimilkservice.vehicle.repository;

import com.sasimilkservice.vehicle.domain.VehicleTrip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VehicleTripRepository extends JpaRepository<VehicleTrip, Long> {

    List<VehicleTrip> findByVehicleIdOrderByStartedAtDesc(Long vehicleId);

    List<VehicleTrip> findByVehicleIdAndStartedAtBetweenOrderByStartedAtDesc(
            Long vehicleId, Instant from, Instant to);

    Optional<VehicleTrip> findByVehicleIdAndStatus(Long vehicleId, VehicleTrip.Status status);
}
