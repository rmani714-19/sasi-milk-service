package com.sasimilkservice.vehicle.repository;

import com.sasimilkservice.vehicle.domain.VehicleLocationHistory;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VehicleLocationHistoryRepository extends JpaRepository<VehicleLocationHistory, Long> {

    Optional<VehicleLocationHistory> findFirstByVehicleIdOrderByProviderTimestampDesc(Long vehicleId);

    List<VehicleLocationHistory> findByVehicleIdOrderByProviderTimestampDesc(Long vehicleId, Limit limit);

    List<VehicleLocationHistory> findByVehicleIdAndProviderTimestampBetweenOrderByProviderTimestampAsc(
            Long vehicleId, Instant from, Instant to);
}
