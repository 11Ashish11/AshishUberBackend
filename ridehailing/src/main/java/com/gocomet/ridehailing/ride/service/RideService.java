package com.gocomet.ridehailing.ride.service;

import com.gocomet.ridehailing.common.exception.DuplicateRequestException;
import com.gocomet.ridehailing.common.exception.InvalidStateTransitionException;
import com.gocomet.ridehailing.common.exception.ResourceNotFoundException;
import com.gocomet.ridehailing.driver.model.Driver;
import com.gocomet.ridehailing.driver.model.DriverStatus;
import com.gocomet.ridehailing.driver.repository.DriverRepository;
import com.gocomet.ridehailing.driver.service.LocationService;
import com.gocomet.ridehailing.notification.service.NotificationService;
import com.gocomet.ridehailing.pricing.service.SurgePricingService;
import com.gocomet.ridehailing.ride.dto.RideRequest;
import com.gocomet.ridehailing.ride.dto.RideResponse;
import com.gocomet.ridehailing.ride.event.RideEventProducer;
import com.gocomet.ridehailing.ride.model.*;
import com.gocomet.ridehailing.ride.repository.RideAssignmentRepository;
import com.gocomet.ridehailing.ride.repository.RideRepository;
import com.gocomet.ridehailing.rider.model.Rider;
import com.gocomet.ridehailing.rider.repository.RiderRepository;
import com.gocomet.ridehailing.trip.model.Trip;
import com.gocomet.ridehailing.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RideService {

        private final RideRepository rideRepository;
        private final RiderRepository riderRepository;
        private final DriverRepository driverRepository;
        private final RideAssignmentRepository rideAssignmentRepository;
        private final TripRepository tripRepository;
        private final MatchingService matchingService;
        private final SurgePricingService surgePricingService;
        private final LocationService locationService;
        private final NotificationService notificationService;
        private final RideEventProducer rideEventProducer;

        /**
         * Create a new ride request.
         * 1. Validate rider exists
         * 2. Check idempotency
         * 3. Check rider doesn't have an active ride
         * 4. Calculate surge and estimated fare
         * 5. Save ride
         * 6. Trigger matching
         */
        @Transactional
        public RideResponse createRide(RideRequest request) {
                // Idempotency check
                if (request.getIdempotencyKey() != null) {
                        Optional<Ride> existing = rideRepository.findByIdempotencyKey(request.getIdempotencyKey());
                        if (existing.isPresent()) {
                                log.info("Duplicate ride request with idempotency key: {}",
                                                request.getIdempotencyKey());
                                return toResponse(existing.get());
                        }
                }

                // Validate rider
                Rider rider = riderRepository.findById(request.getRiderId())
                                .orElseThrow(() -> new ResourceNotFoundException("Rider", "id", request.getRiderId()));

                // Check for active rides (prevent double-booking)
                List<RideStatus> activeStatuses = List.of(
                                RideStatus.REQUESTED, RideStatus.MATCHING, RideStatus.MATCHED, RideStatus.ACCEPTED);
                if (rideRepository.existsByRiderIdAndStatusIn(rider.getId(), activeStatuses)) {
                        throw new DuplicateRequestException("Rider already has an active ride");
                }

                // Calculate surge
                BigDecimal surge = surgePricingService.getSurgeMultiplier(
                                request.getPickupLat(), request.getPickupLng());

                // Record demand for surge calculation
                surgePricingService.recordDemand(request.getPickupLat(), request.getPickupLng());

                // Estimate fare
                BigDecimal estimatedFare = surgePricingService.estimateFare(
                                request.getPickupLat(), request.getPickupLng(),
                                request.getDestinationLat(), request.getDestinationLng(),
                                request.getVehicleTier().name(), surge);

                // Create ride
                Ride ride = Ride.builder()
                                .rider(rider)
                                .pickupLat(request.getPickupLat())
                                .pickupLng(request.getPickupLng())
                                .destinationLat(request.getDestinationLat())
                                .destinationLng(request.getDestinationLng())
                                .vehicleTier(request.getVehicleTier())
                                .status(RideStatus.REQUESTED)
                                .surgeMultiplier(surge)
                                .estimatedFare(estimatedFare)
                                .idempotencyKey(request.getIdempotencyKey() != null
                                                ? request.getIdempotencyKey()
                                                : UUID.randomUUID().toString())
                                .build();

                ride = rideRepository.save(ride);
                log.info("Ride {} created for rider {}", ride.getId(), rider.getId());

                // Publish REQUESTED event to Kafka
                rideEventProducer.publishRideRequested(ride.getId(), rider.getId());

                // Trigger matching (async in production, synchronous here for simplicity)
                matchingService.findAndAssignDriver(ride.getId());

                // Reload to get updated status after matching
                ride = rideRepository.findById(ride.getId()).orElse(ride);

                return toResponse(ride);
        }

        /**
         * Get ride status.
         */
        public RideResponse getRide(UUID rideId) {
                Ride ride = rideRepository.findById(rideId)
                                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));
                return toResponse(ride);
        }

        /**
         * Driver accepts a ride assignment.
         * 1. Validate the assignment
         * 2. Update assignment status
         * 3. Update ride status
         * 4. Update driver status to ON_TRIP
         * 5. Create trip
         * 6. Notify rider
         */
        @Transactional
        public RideResponse acceptRide(UUID driverId, UUID rideId) {
                Ride ride = rideRepository.findById(rideId)
                                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));

                if (ride.getStatus() != RideStatus.MATCHED) {
                        throw new InvalidStateTransitionException("Ride", ride.getStatus().name(), "ACCEPTED");
                }

                // Find and validate the assignment
                RideAssignment assignment = rideAssignmentRepository
                                .findByRideIdAndDriverId(rideId, driverId)
                                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "ride+driver",
                                                rideId + "+" + driverId));

                if (assignment.getStatus() != AssignmentStatus.OFFERED) {
                        throw new InvalidStateTransitionException("Assignment", assignment.getStatus().name(),
                                        "ACCEPTED");
                }

                Driver driver = driverRepository.findById(driverId)
                                .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", driverId));

                // Update assignment
                assignment.setStatus(AssignmentStatus.ACCEPTED);
                assignment.setRespondedAt(LocalDateTime.now());
                rideAssignmentRepository.save(assignment);

                // Update ride
                ride.setStatus(RideStatus.ACCEPTED);
                ride.setAssignedDriver(driver);
                rideRepository.save(ride);

                // Update driver status
                driver.setStatus(DriverStatus.ON_TRIP);
                driverRepository.save(driver);

                // Remove driver from availability pool
                locationService.removeDriverAvailability(driverId);
                locationService.unlockDriver(driverId);

                // Create trip
                Trip trip = Trip.builder()
                                .ride(ride)
                                .driver(driver)
                                .rider(ride.getRider())
                                .startTime(LocalDateTime.now())
                                .startLat(ride.getPickupLat())
                                .startLng(ride.getPickupLng())
                                .surgeMultiplier(ride.getSurgeMultiplier())
                                .build();
                tripRepository.save(trip);

                // Publish DRIVER_ASSIGNED and TRIP_STARTED events to Kafka
                rideEventProducer.publishDriverAssigned(rideId, ride.getRider().getId(), driverId);
                rideEventProducer.publishTripStarted(rideId, ride.getRider().getId(), driverId);

                // Notify rider
                notificationService.notifyRider(ride.getRider().getId(), "RIDE_ACCEPTED", Map.ofEntries(
                                Map.entry("rideId", rideId.toString()),
                                Map.entry("tripId", trip.getId().toString()),
                                Map.entry("driverName", driver.getName()),
                                Map.entry("driverId", driverId.toString()),
                                Map.entry("vehicleType", driver.getVehicleType().name())));

                log.info("Driver {} accepted ride {}. Trip {} created.", driverId, rideId, trip.getId());

                // Build response with tripId
                RideResponse response = toResponse(ride);
                response.setTripId(trip.getId());
                return response;
        }

        /**
         * Cancel a ride.
         */
        @Transactional
        public RideResponse cancelRide(UUID rideId) {
                Ride ride = rideRepository.findById(rideId)
                                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));

                List<RideStatus> cancellableStatuses = List.of(
                                RideStatus.REQUESTED, RideStatus.MATCHING, RideStatus.MATCHED);
                if (!cancellableStatuses.contains(ride.getStatus())) {
                        throw new InvalidStateTransitionException("Ride", ride.getStatus().name(), "CANCELLED");
                }

                // If a driver was matched, unlock them
                if (ride.getAssignedDriver() != null) {
                        locationService.unlockDriver(ride.getAssignedDriver().getId());
                }

                ride.setStatus(RideStatus.CANCELLED);
                rideRepository.save(ride);

                // Publish CANCELLED event to Kafka
                rideEventProducer.publishRideCancelled(rideId, ride.getRider().getId(), "Cancelled by rider");

                log.info("Ride {} cancelled", rideId);
                return toResponse(ride);
        }

        /**
         * Get pending ride offers for a driver.
         * Returns rides that have been offered to this driver but not yet
         * accepted/declined.
         */
        public List<Map<String, Object>> getPendingOffersForDriver(UUID driverId) {
                // Validate driver exists
                Driver driver = driverRepository.findById(driverId)
                                .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", driverId));

                // Find all OFFERED assignments for this driver
                List<RideAssignment> pendingAssignments = rideAssignmentRepository
                                .findByDriverIdAndStatus(driverId, AssignmentStatus.OFFERED);

                // Build response for each pending offer
                return pendingAssignments.stream()
                                .map(assignment -> {
                                        Ride ride = assignment.getRide();
                                        Map<String, Object> offer = Map.ofEntries(
                                                        Map.entry("type", "RIDE_OFFER"),
                                                        Map.entry("rideId", ride.getId().toString()),
                                                        Map.entry("riderId", ride.getRider().getId().toString()),
                                                        Map.entry("pickupLat", ride.getPickupLat()),
                                                        Map.entry("pickupLng", ride.getPickupLng()),
                                                        Map.entry("destinationLat", ride.getDestinationLat()),
                                                        Map.entry("destinationLng", ride.getDestinationLng()),
                                                        Map.entry("vehicleTier", ride.getVehicleTier().name()),
                                                        Map.entry("estimatedFare", ride.getEstimatedFare()),
                                                        Map.entry("surgeMultiplier", ride.getSurgeMultiplier()),
                                                        Map.entry("assignmentStatus", "OFFERED"),
                                                        Map.entry("offeredAt", assignment.getOfferedAt().toString()));
                                        return offer;
                                })
                                .collect(Collectors.toList());
        }

        private RideResponse toResponse(Ride ride) {
                RideResponse.RideResponseBuilder builder = RideResponse.builder()
                                .id(ride.getId())
                                .riderId(ride.getRider().getId())
                                .pickupLat(ride.getPickupLat())
                                .pickupLng(ride.getPickupLng())
                                .destinationLat(ride.getDestinationLat())
                                .destinationLng(ride.getDestinationLng())
                                .vehicleTier(ride.getVehicleTier())
                                .status(ride.getStatus())
                                .surgeMultiplier(ride.getSurgeMultiplier())
                                .estimatedFare(ride.getEstimatedFare())
                                .createdAt(ride.getCreatedAt());

                if (ride.getAssignedDriver() != null) {
                        builder.assignedDriverId(ride.getAssignedDriver().getId());
                        builder.assignedDriverName(ride.getAssignedDriver().getName());
                }

                return builder.build();
        }
}
