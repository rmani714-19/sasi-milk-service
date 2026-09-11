package com.sasimilkservice.vehicle.repository;

import com.sasimilkservice.vehicle.domain.VehicleDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleDeviceRepository extends JpaRepository<VehicleDevice, Long> {

    List<VehicleDevice> findByVehicleId(Long vehicleId);

    List<VehicleDevice> findByVehicleIdAndStatus(Long vehicleId, VehicleDevice.Status status);

    Optional<VehicleDevice> findByImei(String imei);

    Optional<VehicleDevice> findByProviderAndExternalDeviceId(String provider, String externalDeviceId);

    /** Devices not currently attached to any vehicle and fit to be assigned. */
    List<VehicleDevice> findByVehicleIdIsNullAndStatus(VehicleDevice.Status status);

    List<VehicleDevice> findAllByOrderByIdDesc();
}
