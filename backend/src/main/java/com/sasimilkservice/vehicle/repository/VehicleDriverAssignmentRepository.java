package com.sasimilkservice.vehicle.repository;

import com.sasimilkservice.vehicle.domain.VehicleDriverAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleDriverAssignmentRepository extends JpaRepository<VehicleDriverAssignment, Long> {

    List<VehicleDriverAssignment> findByVehicleIdOrderByIdDesc(Long vehicleId);

    Optional<VehicleDriverAssignment> findByVehicleIdAndAssignedToIsNull(Long vehicleId);
}
