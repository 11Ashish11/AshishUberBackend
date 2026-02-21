package com.gocomet.ridehailing.driver.service;

import com.gocomet.ridehailing.common.exception.ResourceNotFoundException;
import com.gocomet.ridehailing.driver.dto.DriverResponse;
import com.gocomet.ridehailing.driver.dto.LocationUpdateRequest;
import com.gocomet.ridehailing.driver.event.DriverLocationProducer;
import com.gocomet.ridehailing.driver.model.Driver;
import com.gocomet.ridehailing.driver.model.DriverStatus;
import com.gocomet.ridehailing.driver.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverService {

    private final DriverRepository driverRepository;
    private final LocationService locationService;
    private final DriverLocationProducer driverLocationProducer;

    /**
     * Process a location update from a driver.
     * Updates both Redis (for fast matching) and Postgres (for persistence).
     */
    @Transactional
    public DriverResponse updateLocation(UUID driverId, LocationUpdateRequest request) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", driverId));

        // Update Postgres (source of truth)
        driver.setCurrentLat(request.getLatitude());
        driver.setCurrentLng(request.getLongitude());

        // If driver is available, update Redis geo index
        if (driver.getStatus() == DriverStatus.AVAILABLE) {
            locationService.updateDriverLocation(
                    driverId,
                    request.getLatitude(),
                    request.getLongitude(),
                    driver.getVehicleType().name());
        }

        driverRepository.save(driver);

        // Publish location update to Kafka
        driverLocationProducer.publishLocation(driver);

        return toResponse(driver);
    }

    /**
     * Set driver to AVAILABLE status and add to Redis pool.
     */
    @Transactional
    public DriverResponse goOnline(UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", driverId));

        driver.setStatus(DriverStatus.AVAILABLE);
        driverRepository.save(driver);

        // Add to Redis if location is known
        if (driver.getCurrentLat() != null && driver.getCurrentLng() != null) {
            locationService.updateDriverLocation(
                    driverId,
                    driver.getCurrentLat(),
                    driver.getCurrentLng(),
                    driver.getVehicleType().name());
        }

        log.info("Driver {} is now AVAILABLE", driverId);
        return toResponse(driver);
    }

    /**
     * Set driver to OFFLINE status and remove from Redis pool.
     */
    @Transactional
    public DriverResponse goOffline(UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", driverId));

        driver.setStatus(DriverStatus.OFFLINE);
        driverRepository.save(driver);
        locationService.removeDriverAvailability(driverId);

        log.info("Driver {} is now OFFLINE", driverId);
        return toResponse(driver);
    }

    /**
     * Fetch all drivers (used by frontend for driver selection/demo).
     */
    public List<DriverResponse> getAllDrivers() {
        return driverRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Driver getDriverEntity(UUID driverId) {
        return driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", driverId));
    }

    public DriverResponse toResponse(Driver driver) {
        return DriverResponse.builder()
                .id(driver.getId())
                .name(driver.getName())
                .vehicleType(driver.getVehicleType())
                .status(driver.getStatus())
                .currentLat(driver.getCurrentLat())
                .currentLng(driver.getCurrentLng())
                .build();
    }
}
