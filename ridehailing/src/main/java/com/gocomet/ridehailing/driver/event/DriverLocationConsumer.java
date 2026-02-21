package com.gocomet.ridehailing.driver.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Consumes driver GPS location updates from the "driver-locations" topic.
 *
 * Consumer group: "driver-location-tracker"
 * — dedicated group so this consumer processes all location events
 * independently from other consumers on the same topic.
 *
 * Because messages are keyed by driverId, all updates for a single driver
 * arrive in order within the same partition — making per-driver state safe.
 *
 * Current scope: logging.
 * Future: update in-memory geo-index, push to frontend map via WebSocket,
 * feed real-time analytics, or write to a time-series store.
 */
@Service
@Slf4j
public class DriverLocationConsumer {

    @KafkaListener(topics = "${app.kafka.topics.driver-locations}", groupId = "driver-location-tracker")
    public void consume(
            @Payload Map<String, Object> location,
            @Header(KafkaHeaders.RECEIVED_KEY) String driverId,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.debug(
                "📍 DriverLocation — driverId={}, lat={}, lng={}, status={}, vehicleType={} | partition={}, offset={}",
                driverId,
                location.get("latitude"),
                location.get("longitude"),
                location.get("status"),
                location.get("vehicleType"),
                partition,
                offset);

        // TODO: downstream actions:
        // - Broadcast to /topic/locations via WebSocket
        // (NotificationService.broadcastDriverLocation)
        // - Feed a real-time geo-index for analytics dashboards
        // - Write to time-series store for replay / debugging
    }
}
