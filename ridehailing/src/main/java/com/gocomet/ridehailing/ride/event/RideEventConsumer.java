package com.gocomet.ridehailing.ride.event;

import com.gocomet.ridehailing.common.event.RideEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Consumes ride state-change events from the "ride-events" topic.
 *
 * Consumer group: "ride-state-tracker"
 * — independent from other consumer groups so this consumer always gets
 * every event even if a separate notification or billing consumer exists.
 *
 * Because all events for a ride are keyed by rideId they arrive in order
 * within a partition, so this consumer sees events in the correct sequence
 * without any locking or coordination.
 *
 * Current scope: logging + placeholder comments for downstream work.
 * Future: persist state to Redis/DB, trigger notifications, billing, etc.
 */
@Service
@Slf4j
public class RideEventConsumer {

    @KafkaListener(topics = "${app.kafka.topics.ride-events}", groupId = "ride-state-tracker")
    public void consume(
            @Payload RideEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("📥 RideEvent [{}] — rideId={}, riderId={}, driverId={} | partition={}, offset={}",
                event.getEventType(),
                event.getRideId(),
                event.getRiderId(),
                event.getDriverId(),
                partition,
                offset);

        switch (event.getEventType()) {
            case REQUESTED -> log.debug("   ↳ Ride requested, matching in progress...");
            case DRIVER_ASSIGNED -> log.info("   ↳ Driver {} assigned to ride {}",
                    event.getDriverId(), event.getRideId());
            case TRIP_STARTED -> log.info("   ↳ Trip started for ride {}", event.getRideId());
            case TRIP_COMPLETED -> log.info("   ↳ Trip completed. Fare details: {}", event.getMetadata());
            case CANCELLED -> log.info("   ↳ Ride {} cancelled. Reason: {}", event.getRideId(), event.getMetadata());
            case NO_DRIVERS -> log.warn("   ↳ No drivers found for ride {}", event.getRideId());
            default -> log.debug("   ↳ Event type {} received", event.getEventType());
        }

        // TODO: downstream actions (keep these for future integration):
        // - DRIVER_ASSIGNED → push WebSocket notification to rider (outside this tx)
        // - TRIP_COMPLETED → trigger billing service
        // - CANCELLED → release surge demand counter in Redis
        // - NO_DRIVERS → retry logic / notify rider
    }
}
