package com.gocomet.ridehailing.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a state-change event in the ride lifecycle.
 *
 * Published to the "ride-events" Kafka topic.
 * Keyed by rideId so all events for a single ride land in the same partition,
 * guaranteeing ordered processing by consumers.
 *
 * Event flow:
 * REQUESTED → DRIVER_ASSIGNED → DRIVER_EN_ROUTE → DRIVER_ARRIVED
 * → TRIP_STARTED → TRIP_COMPLETED → PAYMENT_COMPLETED
 * (from any state) → CANCELLED | NO_DRIVERS
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideEvent {

    private String eventId;
    private UUID rideId;
    private UUID riderId;
    private UUID driverId; // null for REQUESTED / NO_DRIVERS events
    private EventType eventType;
    private Instant timestamp;
    private String metadata; // Extra JSON: fare details, cancellation reason, etc.

    public enum EventType {
        REQUESTED,
        DRIVER_ASSIGNED,
        DRIVER_EN_ROUTE,
        DRIVER_ARRIVED,
        TRIP_STARTED,
        TRIP_COMPLETED,
        PAYMENT_COMPLETED,
        CANCELLED,
        NO_DRIVERS
    }

    // ── Factory helpers ────────────────────────────────────────────────────

    public static RideEvent requested(UUID rideId, UUID riderId) {
        return RideEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .rideId(rideId)
                .riderId(riderId)
                .eventType(EventType.REQUESTED)
                .timestamp(Instant.now())
                .build();
    }

    public static RideEvent driverAssigned(UUID rideId, UUID riderId, UUID driverId) {
        return RideEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .rideId(rideId)
                .riderId(riderId)
                .driverId(driverId)
                .eventType(EventType.DRIVER_ASSIGNED)
                .timestamp(Instant.now())
                .build();
    }

    public static RideEvent tripStarted(UUID rideId, UUID riderId, UUID driverId) {
        return RideEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .rideId(rideId)
                .riderId(riderId)
                .driverId(driverId)
                .eventType(EventType.TRIP_STARTED)
                .timestamp(Instant.now())
                .build();
    }

    public static RideEvent tripCompleted(UUID rideId, UUID riderId, UUID driverId, String fareJson) {
        return RideEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .rideId(rideId)
                .riderId(riderId)
                .driverId(driverId)
                .eventType(EventType.TRIP_COMPLETED)
                .timestamp(Instant.now())
                .metadata(fareJson)
                .build();
    }

    public static RideEvent cancelled(UUID rideId, UUID riderId, String reason) {
        return RideEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .rideId(rideId)
                .riderId(riderId)
                .eventType(EventType.CANCELLED)
                .timestamp(Instant.now())
                .metadata(reason)
                .build();
    }

    public static RideEvent noDrivers(UUID rideId, UUID riderId) {
        return RideEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .rideId(rideId)
                .riderId(riderId)
                .eventType(EventType.NO_DRIVERS)
                .timestamp(Instant.now())
                .build();
    }
}
