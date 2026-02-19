package com.gocomet.ridehailing.trip.controller;

import com.gocomet.ridehailing.trip.dto.TripEndRequest;
import com.gocomet.ridehailing.trip.dto.TripResponse;
import com.gocomet.ridehailing.trip.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    /**
     * POST /v1/trips/{id}/end — End trip and trigger fare calculation
     */
    @PostMapping("/{id}/end")
    public ResponseEntity<TripResponse> endTrip(
            @PathVariable UUID id,
            @Valid @RequestBody TripEndRequest request) {

        return ResponseEntity.ok(tripService.endTrip(id, request));
    }

    /**
     * GET /v1/trips/{id} — Get trip details
     */
    @GetMapping("/{id}")
    public ResponseEntity<TripResponse> getTrip(@PathVariable UUID id) {
        return ResponseEntity.ok(tripService.getTrip(id));
    }
}
