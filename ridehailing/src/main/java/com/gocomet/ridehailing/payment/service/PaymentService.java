package com.gocomet.ridehailing.payment.service;

import com.gocomet.ridehailing.common.exception.DuplicateRequestException;
import com.gocomet.ridehailing.common.exception.InvalidStateTransitionException;
import com.gocomet.ridehailing.common.exception.ResourceNotFoundException;
import com.gocomet.ridehailing.notification.service.NotificationService;
import com.gocomet.ridehailing.payment.dto.PaymentRequest;
import com.gocomet.ridehailing.payment.dto.PaymentResponse;
import com.gocomet.ridehailing.payment.model.Payment;
import com.gocomet.ridehailing.payment.model.PaymentStatus;
import com.gocomet.ridehailing.payment.repository.PaymentRepository;
import com.gocomet.ridehailing.trip.model.Trip;
import com.gocomet.ridehailing.trip.model.TripStatus;
import com.gocomet.ridehailing.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final TripRepository tripRepository;
    private final PspStubService pspStubService;
    private final NotificationService notificationService;

    /**
     * Process payment for a completed trip.
     * 1. Check idempotency
     * 2. Validate trip is completed and has a fare
     * 3. Create payment record
     * 4. Call PSP stub
     * 5. Update payment status
     * 6. Notify rider
     */
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        // Idempotency check
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Duplicate payment request with key: {}", request.getIdempotencyKey());
            return toResponse(existing.get());
        }

        // Validate trip
        Trip trip = tripRepository.findById(request.getTripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", request.getTripId()));

        if (trip.getStatus() != TripStatus.COMPLETED) {
            throw new InvalidStateTransitionException("Cannot process payment for non-completed trip");
        }

        if (trip.getTotalFare() == null) {
            throw new IllegalArgumentException("Trip fare has not been calculated yet");
        }

        // Check if payment already exists for this trip
        Optional<Payment> existingPayment = paymentRepository.findByTripId(trip.getId());
        if (existingPayment.isPresent() && existingPayment.get().getStatus() == PaymentStatus.SUCCESS) {
            throw new DuplicateRequestException("Payment already processed for this trip");
        }

        // Create payment record
        Payment payment = Payment.builder()
                .trip(trip)
                .rider(trip.getRider())
                .amount(trip.getTotalFare())
                .paymentMethod(request.getPaymentMethod())
                .status(PaymentStatus.PROCESSING)
                .idempotencyKey(request.getIdempotencyKey())
                .build();
        payment = paymentRepository.save(payment);

        // Call PSP stub
        Map<String, Object> pspResult = pspStubService.processPayment(
                request.getIdempotencyKey(),
                trip.getTotalFare(),
                payment.getCurrency()
        );

        // Update payment based on PSP response
        String pspStatus = (String) pspResult.get("status");
        if ("SUCCESS".equals(pspStatus)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPspTransactionId((String) pspResult.get("pspTransactionId"));
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }
        payment = paymentRepository.save(payment);

        // Notify rider
        notificationService.notifyRider(trip.getRider().getId(), "PAYMENT_" + payment.getStatus(), Map.of(
                "paymentId", payment.getId().toString(),
                "amount", payment.getAmount().toString(),
                "status", payment.getStatus().name()
        ));

        log.info("Payment {} for trip {} — status: {}", payment.getId(), trip.getId(), payment.getStatus());
        return toResponse(payment);
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .tripId(payment.getTrip().getId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .pspTransactionId(payment.getPspTransactionId())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
