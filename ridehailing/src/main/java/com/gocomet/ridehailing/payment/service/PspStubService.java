package com.gocomet.ridehailing.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates an external Payment Service Provider (like Razorpay/Stripe).
 * 
 * Behavior:
 * - 90% success rate
 * - Random delay 200ms-1500ms (simulates network latency)
 * - Idempotent (same idempotency key returns same result)
 */
@Service
@Slf4j
public class PspStubService {

    private final Random random = new Random();

    // In-memory store for idempotency (simulates PSP's dedup)
    private final ConcurrentHashMap<String, Map<String, Object>> processedPayments = new ConcurrentHashMap<>();

    /**
     * Process a payment through the "PSP".
     * Returns a map with status and transaction ID.
     */
    public Map<String, Object> processPayment(String idempotencyKey, BigDecimal amount, String currency) {
        // Idempotency check — if we've seen this key before, return same result
        if (processedPayments.containsKey(idempotencyKey)) {
            log.info("PSP Stub: Duplicate payment request with key {}. Returning cached result.", idempotencyKey);
            return processedPayments.get(idempotencyKey);
        }

        // Simulate network latency
        try {
            int delay = 200 + random.nextInt(1300); // 200ms to 1500ms
            Thread.sleep(delay);
            log.debug("PSP Stub: Simulated {}ms network delay", delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate 90% success rate
        boolean success = random.nextInt(10) < 9;

        Map<String, Object> result;
        if (success) {
            String pspTxnId = "psp_" + UUID.randomUUID().toString().substring(0, 12);
            result = Map.of(
                    "status", "SUCCESS",
                    "pspTransactionId", pspTxnId,
                    "amount", amount.toString(),
                    "currency", currency
            );
            log.info("PSP Stub: Payment SUCCESS for key {} — txn: {}", idempotencyKey, pspTxnId);
        } else {
            result = Map.of(
                    "status", "FAILED",
                    "pspTransactionId", "",
                    "amount", amount.toString(),
                    "currency", currency,
                    "error", "Insufficient funds"
            );
            log.info("PSP Stub: Payment FAILED for key {}", idempotencyKey);
        }

        // Store for idempotency
        processedPayments.put(idempotencyKey, result);
        return result;
    }
}
