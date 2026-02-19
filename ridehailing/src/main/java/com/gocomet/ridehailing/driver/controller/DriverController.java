package com.gocomet.ridehailing.driver.controller;

import com.gocomet.ridehailing.driver.dto.DriverResponse;
import com.gocomet.ridehailing.driver.dto.LocationUpdateRequest;
import com.gocomet.ridehailing.driver.service.DriverService;
import com.gocomet.ridehailing.notification.service.NotificationService;
import com.gocomet.ridehailing.ride.service.MatchingService;
import com.gocomet.ridehailing.ride.service.RideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;
    private final RideService rideService;
    private final MatchingService matchingService;
    private final NotificationService notificationService;

    /**
     * POST /v1/drivers/{id}/location — Send driver location update
     */
    @PostMapping("/{id}/location")
    public ResponseEntity<DriverResponse> updateLocation(
            @PathVariable UUID id,
            @Valid @RequestBody LocationUpdateRequest request) {

        DriverResponse response = driverService.updateLocation(id, request);

        // Broadcast location to frontend for live map
        notificationService.broadcastDriverLocation(id, request.getLatitude(), request.getLongitude());

        return ResponseEntity.ok(response);
    }

    /**
     * POST /v1/drivers/{id}/accept — Accept a ride assignment
     */
    @PostMapping("/{id}/accept")
    public ResponseEntity<?> acceptRide(
            @PathVariable UUID id,
            @RequestParam UUID rideId) {

        return ResponseEntity.ok(rideService.acceptRide(id, rideId));
    }

    /**
     * POST /v1/drivers/{id}/decline — Decline a ride assignment
     */
    @PostMapping("/{id}/decline")
    public ResponseEntity<?> declineRide(
            @PathVariable UUID id,
            @RequestParam UUID rideId) {

        matchingService.handleDriverDecline(rideId, id);
        return ResponseEntity.ok(Map.of("message", "Ride declined. Looking for another driver."));
    }

    /**
     * POST /v1/drivers/{id}/online — Go online
     */
    @PostMapping("/{id}/online")
    public ResponseEntity<DriverResponse> goOnline(@PathVariable UUID id) {
        return ResponseEntity.ok(driverService.goOnline(id));
    }

    /**
     * POST /v1/drivers/{id}/offline — Go offline
     */
    @PostMapping("/{id}/offline")
    public ResponseEntity<DriverResponse> goOffline(@PathVariable UUID id) {
        return ResponseEntity.ok(driverService.goOffline(id));
    }
}
