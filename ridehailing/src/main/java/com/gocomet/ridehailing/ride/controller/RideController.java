package com.gocomet.ridehailing.ride.controller;

import com.gocomet.ridehailing.ride.dto.RideRequest;
import com.gocomet.ridehailing.ride.dto.RideResponse;
import com.gocomet.ridehailing.ride.service.RideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/rides")
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService;

    /**
     * POST /v1/rides — Create a ride request
     */
    @PostMapping
    public ResponseEntity<RideResponse> createRide(@Valid @RequestBody RideRequest request) {
        RideResponse response = rideService.createRide(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /v1/rides/{id} — Get ride status
     */
    @GetMapping("/{id}")
    public ResponseEntity<RideResponse> getRide(@PathVariable UUID id) {
        return ResponseEntity.ok(rideService.getRide(id));
    }

    /**
     * POST /v1/rides/{id}/cancel — Cancel a ride
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<RideResponse> cancelRide(@PathVariable UUID id) {
        return ResponseEntity.ok(rideService.cancelRide(id));
    }
}
