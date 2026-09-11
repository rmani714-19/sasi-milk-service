package com.sasimilkservice.vehicle.repository;

import com.sasimilkservice.vehicle.domain.VehicleCamera;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleCameraRepository extends JpaRepository<VehicleCamera, Long> {

    List<VehicleCamera> findByVehicleId(Long vehicleId);

    List<VehicleCamera> findByDeviceId(Long deviceId);
}
