package com.gocomet.ridehailing.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Notify a rider about ride status updates.
     * Frontend subscribes to: /topic/rider/{riderId}
     */
    public void notifyRider(UUID riderId, String eventType, Object payload) {
        String destination = "/topic/rider/" + riderId;
        Map<String, Object> message = Map.of(
                "eventType", eventType,
                "payload", payload
        );
        messagingTemplate.convertAndSend(destination, (Object) message);
        log.debug("Notified rider {} with event: {}", riderId, eventType);
    }

    /**
     * Notify a driver about ride offers or updates.
     * Frontend subscribes to: /topic/driver/{driverId}
     */
    public void notifyDriver(UUID driverId, String eventType, Object payload) {
        String destination = "/topic/driver/" + driverId;
        Map<String, Object> message = Map.of(
                "eventType", eventType,
                "payload", payload
        );
        messagingTemplate.convertAndSend(destination, (Object) message);
        log.debug("Notified driver {} with event: {}", driverId, eventType);
    }

    /**
     * Broadcast driver location update (for the frontend map).
     * Frontend subscribes to: /topic/locations
     */
    public void broadcastDriverLocation(UUID driverId, double lat, double lng) {
        String destination = "/topic/locations";
        Map<String, Object> message = Map.of(
                "driverId", driverId.toString(),
                "lat", lat,
                "lng", lng
        );
        messagingTemplate.convertAndSend(destination, (Object) message);
    }
}
