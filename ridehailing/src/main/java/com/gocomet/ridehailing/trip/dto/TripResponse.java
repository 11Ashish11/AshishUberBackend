package com.gocomet.ridehailing.trip.dto;

import com.gocomet.ridehailing.trip.model.TripStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripResponse {

    private UUID id;
    private UUID rideId;
    private UUID driverId;
    private UUID riderId;
    private TripStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal distanceKm;
    private BigDecimal baseFare;
    private BigDecimal surgeMultiplier;
    private BigDecimal totalFare;
}
