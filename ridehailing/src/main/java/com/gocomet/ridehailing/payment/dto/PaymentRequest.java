package com.gocomet.ridehailing.payment.dto;

import com.gocomet.ridehailing.payment.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {

    @NotNull(message = "Trip ID is required")
    private UUID tripId;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @NotNull(message = "Idempotency key is required")
    private String idempotencyKey;
}
