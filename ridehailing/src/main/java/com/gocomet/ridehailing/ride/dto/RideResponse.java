package com.gocomet.ridehailing.ride.dto;

import com.gocomet.ridehailing.driver.model.VehicleType;
import com.gocomet.ridehailing.ride.model.RideStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideResponse {

    private UUID id;
    private UUID riderId;
    private Double pickupLat;
    private Double pickupLng;
    private Double destinationLat;
    private Double destinationLng;
    private VehicleType vehicleTier;
    private RideStatus status;
    private UUID assignedDriverId;
    private String assignedDriverName;
    private UUID tripId;
    private BigDecimal surgeMultiplier;
    private BigDecimal estimatedFare;
    private LocalDateTime createdAt;
}
