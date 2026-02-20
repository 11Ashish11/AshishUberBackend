package com.gocomet.ridehailing.rider.controller;

import com.gocomet.ridehailing.rider.dto.RiderResponse;
import com.gocomet.ridehailing.rider.service.RiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/riders")
@RequiredArgsConstructor
public class RiderController {

    private final RiderService riderService;

    /**
     * GET /v1/riders — List all riders (for demo/frontend selection)
     */
    @GetMapping
    public ResponseEntity<List<RiderResponse>> getRiders() {
        return ResponseEntity.ok(riderService.getAllRiders());
    }
}

