package com.gocomet.ridehailing.ride.service;

import com.gocomet.ridehailing.common.exception.ResourceNotFoundException;
import com.gocomet.ridehailing.driver.model.Driver;
import com.gocomet.ridehailing.driver.model.DriverStatus;
import com.gocomet.ridehailing.driver.repository.DriverRepository;
import com.gocomet.ridehailing.driver.service.LocationService;
import com.gocomet.ridehailing.notification.service.NotificationService;
import com.gocomet.ridehailing.ride.model.*;
import com.gocomet.ridehailing.ride.repository.RideAssignmentRepository;
import com.gocomet.ridehailing.ride.repository.RideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService {

    private final LocationService locationService;
    private final RideRepository rideRepository;
    private final RideAssignmentRepository rideAssignmentRepository;
    private final DriverRepository driverRepository;
    private final NotificationService notificationService;

    private static final double SEARCH_RADIUS_KM = 5.0;
    private static final int MAX_ASSIGNMENT_ATTEMPTS = 3;

    /**
     * Find and assign a driver for the given ride.
     * This is the core matching logic:
     * 1. Query Redis for nearby available drivers
     * 2. Lock the nearest driver (prevent double-assignment)
     * 3. Create a ride_assignment record
     * 4. Notify the driver
     */
    @Transactional
    public void findAndAssignDriver(UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));

        if (ride.getStatus() != RideStatus.REQUESTED && ride.getStatus() != RideStatus.MATCHING) {
            log.warn("Ride {} is in status {}, cannot match", rideId, ride.getStatus());
            return;
        }

        ride.setStatus(RideStatus.MATCHING);
        rideRepository.save(ride);

        // Find nearby drivers from Redis
        List<UUID> nearbyDrivers = locationService.findNearbyDrivers(
                ride.getPickupLat(),
                ride.getPickupLng(),
                SEARCH_RADIUS_KM,
                ride.getVehicleTier().name()
        );

        if (nearbyDrivers.isEmpty()) {
            log.warn("No nearby drivers found for ride {}", rideId);
            ride.setStatus(RideStatus.NO_DRIVERS_AVAILABLE);
            rideRepository.save(ride);
            notifyRiderNoDrivers(ride);
            return;
        }

        // Try to assign the nearest available driver
        for (UUID driverId : nearbyDrivers) {
            // Check if we already offered this ride to this driver
            if (rideAssignmentRepository.existsByRideIdAndDriverId(rideId, driverId)) {
                continue;
            }

            // Try to acquire lock on this driver
            if (locationService.lockDriver(driverId, rideId)) {
                // Create assignment record
                Driver driver = driverRepository.findById(driverId).orElse(null);
                if (driver == null || driver.getStatus() != DriverStatus.AVAILABLE) {
                    locationService.unlockDriver(driverId);
                    continue;
                }

                RideAssignment assignment = RideAssignment.builder()
                        .ride(ride)
                        .driver(driver)
                        .status(AssignmentStatus.OFFERED)
                        .build();
                rideAssignmentRepository.save(assignment);

                ride.setStatus(RideStatus.MATCHED);
                rideRepository.save(ride);

                // Notify driver about the ride offer
                notificationService.notifyDriver(driverId, "RIDE_OFFER", Map.of(
                        "rideId", rideId.toString(),
                        "pickupLat", ride.getPickupLat(),
                        "pickupLng", ride.getPickupLng(),
                        "destinationLat", ride.getDestinationLat(),
                        "destinationLng", ride.getDestinationLng(),
                        "vehicleTier", ride.getVehicleTier().name(),
                        "estimatedFare", ride.getEstimatedFare() != null ? ride.getEstimatedFare().toString() : "N/A"
                ));

                // Notify rider that a driver was found
                notificationService.notifyRider(ride.getRider().getId(), "DRIVER_MATCHED", Map.of(
                        "rideId", rideId.toString(),
                        "driverName", driver.getName(),
                        "driverId", driverId.toString()
                ));

                log.info("Ride {} matched with driver {}", rideId, driverId);
                return;
            }
        }

        // If no driver could be locked, mark as no drivers available
        log.warn("Could not lock any driver for ride {}", rideId);
        ride.setStatus(RideStatus.NO_DRIVERS_AVAILABLE);
        rideRepository.save(ride);
        notifyRiderNoDrivers(ride);
    }

    /**
     * Handle driver declining a ride offer.
     */
    @Transactional
    public void handleDriverDecline(UUID rideId, UUID driverId) {
        RideAssignment assignment = rideAssignmentRepository
                .findByRideIdAndDriverId(rideId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "ride+driver", rideId + "+" + driverId));

        assignment.setStatus(AssignmentStatus.DECLINED);
        assignment.setRespondedAt(LocalDateTime.now());
        rideAssignmentRepository.save(assignment);

        // Unlock the driver
        locationService.unlockDriver(driverId);

        // Try to find another driver
        log.info("Driver {} declined ride {}. Retrying match...", driverId, rideId);

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));
        ride.setStatus(RideStatus.MATCHING);
        rideRepository.save(ride);

        findAndAssignDriver(rideId);
    }

    private void notifyRiderNoDrivers(Ride ride) {
        notificationService.notifyRider(ride.getRider().getId(), "NO_DRIVERS_AVAILABLE", Map.of(
                "rideId", ride.getId().toString(),
                "message", "No drivers available nearby. Please try again."
        ));
    }
}
