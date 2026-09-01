package com.sasimilkservice.vehicle.repository;

import com.sasimilkservice.vehicle.domain.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByCode(String code);
}
