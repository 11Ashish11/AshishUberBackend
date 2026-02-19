package com.gocomet.ridehailing.payment.dto;

import com.gocomet.ridehailing.payment.model.PaymentMethod;
import com.gocomet.ridehailing.payment.model.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private UUID id;
    private UUID tripId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private PaymentMethod paymentMethod;
    private String pspTransactionId;
    private LocalDateTime createdAt;
}
