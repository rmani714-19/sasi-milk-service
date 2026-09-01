package com.sasimilkservice.vehicle.spi;

import java.time.Instant;
import java.util.List;

/** Published SPI of the {@code vehicle} module — used by the dashboard. */
public interface VehicleStatusQuery {

    List<VehicleStatus> allStatuses();

    record VehicleStatus(
            Long vehicleId,
            String code,
            String registrationNumber,
            boolean online,
            Double latitude,
            Double longitude,
            Double speedKmph,
            Instant lastFixAt) {
    }
}
