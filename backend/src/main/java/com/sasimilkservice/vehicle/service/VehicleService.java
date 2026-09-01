package com.sasimilkservice.vehicle.service;

import com.sasimilkservice.common.error.ConflictException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.vehicle.domain.Vehicle;
import com.sasimilkservice.vehicle.domain.VehicleDevice;
import com.sasimilkservice.vehicle.domain.VehicleLocation;
import com.sasimilkservice.vehicle.repository.VehicleDeviceRepository;
import com.sasimilkservice.vehicle.repository.VehicleLocationRepository;
import com.sasimilkservice.vehicle.repository.VehicleRepository;
import com.sasimilkservice.vehicle.spi.VehicleStatusQuery;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class VehicleService implements VehicleStatusQuery {

    /** A vehicle is "online" if its last GPS fix is within this window. */
    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(15);

    private final VehicleRepository vehicles;
    private final VehicleDeviceRepository devices;
    private final VehicleLocationRepository locations;

    public VehicleService(VehicleRepository vehicles,
                          VehicleDeviceRepository devices,
                          VehicleLocationRepository locations) {
        this.vehicles = vehicles;
        this.devices = devices;
        this.locations = locations;
    }

    public List<Vehicle> findAll() {
        return vehicles.findAll();
    }

    public Vehicle require(long id) {
        return vehicles.findById(id).orElseThrow(() -> NotFoundException.of("Vehicle", id));
    }

    public List<VehicleDevice> devicesOf(long vehicleId) {
        return devices.findByVehicleId(vehicleId);
    }

    public List<VehicleLocation> locationHistory(long vehicleId, int limit) {
        return locations.findByVehicleIdOrderByRecordedAtDesc(vehicleId, Limit.of(Math.min(limit, 500)));
    }

    @Transactional
    public Vehicle create(String code, String registrationNumber, String description,
                          BigDecimal capacityLitres) {
        if (vehicles.findByCode(code).isPresent()) {
            throw new ConflictException("Vehicle code '" + code + "' already exists");
        }
        Vehicle v = new Vehicle();
        v.setCode(code);
        v.setRegistrationNumber(registrationNumber);
        v.setDescription(description);
        v.setCapacityLitres(capacityLitres);
        return vehicles.save(v);
    }

    @Transactional
    public VehicleDevice registerDevice(long vehicleId, VehicleDevice.DeviceType type,
                                        String provider, String externalDeviceId) {
        require(vehicleId);
        VehicleDevice d = new VehicleDevice();
        d.setVehicleId(vehicleId);
        d.setDeviceType(type);
        d.setProvider(provider);
        d.setExternalDeviceId(externalDeviceId);
        return devices.save(d);
    }

    @Transactional
    public VehicleLocation recordLocation(long vehicleId, Instant recordedAt, BigDecimal lat, BigDecimal lng,
                                          BigDecimal speedKmph, BigDecimal headingDeg, Boolean ignition) {
        require(vehicleId);
        VehicleLocation loc = new VehicleLocation();
        loc.setVehicleId(vehicleId);
        loc.setRecordedAt(recordedAt == null ? Instant.now() : recordedAt);
        loc.setLatitude(lat);
        loc.setLongitude(lng);
        loc.setSpeedKmph(speedKmph);
        loc.setHeadingDeg(headingDeg);
        loc.setIgnition(ignition);
        return locations.save(loc);
    }

    @Override
    public List<VehicleStatus> allStatuses() {
        Instant cutoff = Instant.now().minus(ONLINE_WINDOW);
        return vehicles.findAll().stream().map(v -> {
            VehicleLocation last = locations.findFirstByVehicleIdOrderByRecordedAtDesc(v.getId()).orElse(null);
            boolean online = last != null && last.getRecordedAt().isAfter(cutoff);
            return new VehicleStatus(
                    v.getId(), v.getCode(), v.getRegistrationNumber(), online,
                    last == null ? null : last.getLatitude().doubleValue(),
                    last == null ? null : last.getLongitude().doubleValue(),
                    last == null || last.getSpeedKmph() == null ? null : last.getSpeedKmph().doubleValue(),
                    last == null ? null : last.getRecordedAt());
        }).toList();
    }
}
