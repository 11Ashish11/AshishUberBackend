package com.gocomet.ridehailing.ride.dto;

import com.gocomet.ridehailing.driver.model.VehicleType;
import com.gocomet.ridehailing.payment.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideRequest {

    @NotNull(message = "Rider ID is required")
    private UUID riderId;

    @NotNull(message = "Pickup latitude is required")
    private Double pickupLat;

    @NotNull(message = "Pickup longitude is required")
    private Double pickupLng;

    @NotNull(message = "Destination latitude is required")
    private Double destinationLat;

    @NotNull(message = "Destination longitude is required")
    private Double destinationLng;

    @NotNull(message = "Vehicle tier is required")
    private VehicleType vehicleTier;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    // Optional: client-generated idempotency key
    private String idempotencyKey;
}
