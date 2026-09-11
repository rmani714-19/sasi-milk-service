package com.sasimilkservice.vehicle.repository;

import com.sasimilkservice.vehicle.domain.VehicleDeviceAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleDeviceAssignmentRepository extends JpaRepository<VehicleDeviceAssignment, Long> {

    List<VehicleDeviceAssignment> findByVehicleIdOrderByIdDesc(Long vehicleId);

    List<VehicleDeviceAssignment> findByDeviceIdOrderByIdDesc(Long deviceId);

    Optional<VehicleDeviceAssignment> findByDeviceIdAndAssignedToIsNull(Long deviceId);
}
