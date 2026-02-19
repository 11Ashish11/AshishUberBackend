package com.gocomet.ridehailing.payment.controller;

import com.gocomet.ridehailing.payment.dto.PaymentRequest;
import com.gocomet.ridehailing.payment.dto.PaymentResponse;
import com.gocomet.ridehailing.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * POST /v1/payments — Trigger payment flow
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(paymentService.processPayment(request));
    }
}
