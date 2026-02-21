package com.gocomet.ridehailing.driver.event;

import com.gocomet.ridehailing.driver.model.Driver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes driver GPS location updates to the "driver-locations" Kafka topic.
 *
 * KEY = driverId — all locations for a driver go to the same partition so
 * a consumer can maintain per-driver state without cross-partition reads.
 *
 * Fire-and-forget (no callback awaited): missing a single GPS ping is
 * acceptable
 * because the next update arrives within seconds.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DriverLocationProducer {

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    @Value("${app.kafka.topics.driver-locations}")
    private String topic;

    /**
     * Publish a driver's current location and status.
     * Called from DriverService after each successful location update.
     */
    public void publishLocation(UUID driverId, double lat, double lng, String vehicleType, String status) {
        Map<String, Object> payload = Map.of(
                "driverId", driverId.toString(),
                "latitude", lat,
                "longitude", lng,
                "vehicleType", vehicleType,
                "status", status,
                "timestamp", Instant.now().toString());

        kafkaTemplate.send(topic, driverId.toString(), payload);
        log.debug("📍 Driver location event sent: driver={}, ({}, {}), status={}", driverId, lat, lng, status);
    }

    /**
     * Convenience overload that extracts fields directly from a Driver entity.
     */
    public void publishLocation(Driver driver) {
        if (driver.getCurrentLat() == null || driver.getCurrentLng() == null) {
            log.debug("Skipping location publish for driver {} — no coordinates yet", driver.getId());
            return;
        }
        publishLocation(
                driver.getId(),
                driver.getCurrentLat(),
                driver.getCurrentLng(),
                driver.getVehicleType().name(),
                driver.getStatus().name());
    }
}
