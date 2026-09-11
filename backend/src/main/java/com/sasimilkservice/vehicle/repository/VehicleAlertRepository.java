package com.sasimilkservice.vehicle.repository;

import com.sasimilkservice.vehicle.domain.VehicleAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleAlertRepository extends JpaRepository<VehicleAlert, Long> {

    List<VehicleAlert> findAllByOrderByOccurredAtDesc();

    List<VehicleAlert> findByVehicleIdOrderByOccurredAtDesc(Long vehicleId);

    List<VehicleAlert> findByStatusOrderByOccurredAtDesc(VehicleAlert.Status status);

    Optional<VehicleAlert> findByVehicleIdAndAlertTypeAndStatus(
            Long vehicleId, VehicleAlert.AlertType alertType, VehicleAlert.Status status);
}
