package com.sasimilkservice.vehicle.repository;

import com.sasimilkservice.vehicle.domain.VehicleDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleDeviceRepository extends JpaRepository<VehicleDevice, Long> {

    List<VehicleDevice> findByVehicleId(Long vehicleId);
}
