package com.gocomet.ridehailing.ride.event;

import com.gocomet.ridehailing.common.event.RideEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Publishes ride state-change events to the "ride-events" Kafka topic.
 *
 * KEY = rideId — guarantees all events for a ride land in the same partition
 * and are processed in order by consumers.
 *
 * acks=all (configured in application.properties) ensures strong durability
 * for these critical business events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RideEventProducer {

    private final KafkaTemplate<String, RideEvent> kafkaTemplate;

    @Value("${app.kafka.topics.ride-events}")
    private String topic;

    public void publishRideRequested(UUID rideId, UUID riderId) {
        publish(RideEvent.requested(rideId, riderId));
    }

    public void publishDriverAssigned(UUID rideId, UUID riderId, UUID driverId) {
        publish(RideEvent.driverAssigned(rideId, riderId, driverId));
    }

    public void publishTripStarted(UUID rideId, UUID riderId, UUID driverId) {
        publish(RideEvent.tripStarted(rideId, riderId, driverId));
    }

    public void publishTripCompleted(UUID rideId, UUID riderId, UUID driverId, BigDecimal totalFare) {
        String fareJson = String.format("{\"totalFare\": %.2f, \"currency\": \"INR\"}", totalFare);
        publish(RideEvent.tripCompleted(rideId, riderId, driverId, fareJson));
    }

    public void publishRideCancelled(UUID rideId, UUID riderId, String reason) {
        publish(RideEvent.cancelled(rideId, riderId, reason));
    }

    public void publishNoDrivers(UUID rideId, UUID riderId) {
        publish(RideEvent.noDrivers(rideId, riderId));
    }

    private void publish(RideEvent event) {
        String key = event.getRideId().toString();
        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("❌ Failed to publish RideEvent [{}] for ride {}",
                                event.getEventType(), event.getRideId(), ex);
                    } else {
                        log.info("📤 Published RideEvent [{}] for ride {} → partition {}, offset {}",
                                event.getEventType(),
                                event.getRideId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
