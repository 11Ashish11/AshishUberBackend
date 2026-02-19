package com.gocomet.ridehailing.trip.service;

import com.gocomet.ridehailing.common.exception.InvalidStateTransitionException;
import com.gocomet.ridehailing.common.exception.ResourceNotFoundException;
import com.gocomet.ridehailing.driver.model.Driver;
import com.gocomet.ridehailing.driver.model.DriverStatus;
import com.gocomet.ridehailing.driver.repository.DriverRepository;
import com.gocomet.ridehailing.driver.service.LocationService;
import com.gocomet.ridehailing.notification.service.NotificationService;
import com.gocomet.ridehailing.trip.dto.TripEndRequest;
import com.gocomet.ridehailing.trip.dto.TripResponse;
import com.gocomet.ridehailing.trip.model.Trip;
import com.gocomet.ridehailing.trip.model.TripStatus;
import com.gocomet.ridehailing.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripService {

    private final TripRepository tripRepository;
    private final DriverRepository driverRepository;
    private final FareCalculationService fareCalculationService;
    private final LocationService locationService;
    private final NotificationService notificationService;

    /**
     * End a trip and calculate fare.
     * 1. Validate trip is in progress
     * 2. Set end location and time
     * 3. Calculate distance and fare
     * 4. Update driver status back to AVAILABLE
     * 5. Notify rider with fare details
     */
    @Transactional
    public TripResponse endTrip(UUID tripId, TripEndRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getStatus() != TripStatus.IN_PROGRESS) {
            throw new InvalidStateTransitionException("Trip", trip.getStatus().name(), "COMPLETED");
        }

        // Calculate distance
        BigDecimal distanceKm = fareCalculationService.calculateDistanceKm(
                trip.getStartLat(), trip.getStartLng(),
                request.getEndLat(), request.getEndLng()
        );

        // Calculate fare
        String vehicleTier = trip.getRide().getVehicleTier().name();
        BigDecimal totalFare = fareCalculationService.calculateFare(
                trip.getStartLat(), trip.getStartLng(),
                request.getEndLat(), request.getEndLng(),
                vehicleTier, trip.getSurgeMultiplier()
        );

        // Base fare (without surge)
        BigDecimal baseFare = fareCalculationService.calculateFare(
                trip.getStartLat(), trip.getStartLng(),
                request.getEndLat(), request.getEndLng(),
                vehicleTier, BigDecimal.ONE
        );

        // Update trip
        trip.setStatus(TripStatus.COMPLETED);
        trip.setEndTime(LocalDateTime.now());
        trip.setEndLat(request.getEndLat());
        trip.setEndLng(request.getEndLng());
        trip.setDistanceKm(distanceKm);
        trip.setBaseFare(baseFare);
        trip.setTotalFare(totalFare);
        tripRepository.save(trip);

        // Release driver — set back to AVAILABLE
        Driver driver = trip.getDriver();
        driver.setStatus(DriverStatus.AVAILABLE);
        driverRepository.save(driver);

        // Re-add driver to Redis availability pool
        if (driver.getCurrentLat() != null && driver.getCurrentLng() != null) {
            locationService.updateDriverLocation(
                    driver.getId(),
                    driver.getCurrentLat(),
                    driver.getCurrentLng(),
                    driver.getVehicleType().name()
            );
        }

        // Notify rider with fare
        notificationService.notifyRider(trip.getRider().getId(), "TRIP_COMPLETED", Map.of(
                "tripId", tripId.toString(),
                "distanceKm", distanceKm.toString(),
                "baseFare", baseFare.toString(),
                "surgeMultiplier", trip.getSurgeMultiplier().toString(),
                "totalFare", totalFare.toString()
        ));

        log.info("Trip {} completed. Distance: {}km, Fare: {}", tripId, distanceKm, totalFare);
        return toResponse(trip);
    }

    public TripResponse getTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));
        return toResponse(trip);
    }

    private TripResponse toResponse(Trip trip) {
        return TripResponse.builder()
                .id(trip.getId())
                .rideId(trip.getRide().getId())
                .driverId(trip.getDriver().getId())
                .riderId(trip.getRider().getId())
                .status(trip.getStatus())
                .startTime(trip.getStartTime())
                .endTime(trip.getEndTime())
                .distanceKm(trip.getDistanceKm())
                .baseFare(trip.getBaseFare())
                .surgeMultiplier(trip.getSurgeMultiplier())
                .totalFare(trip.getTotalFare())
                .build();
    }
}
