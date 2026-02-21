package com.gocomet.ridehailing.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic definitions for the ride-hailing platform.
 *
 * Topics:
 * ride-requests — commands from riders; keyed by requestId
 * driver-locations — high-volume GPS pings from drivers; keyed by driverId
 * ride-events — event-sourcing log of all ride state transitions; keyed by
 * rideId
 *
 * Partition count is 2 for local dev. Increase significantly for production.
 */
@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.ride-requests}")
    private String rideRequestsTopic;

    @Value("${app.kafka.topics.driver-locations}")
    private String driverLocationsTopic;

    @Value("${app.kafka.topics.ride-events}")
    private String rideEventsTopic;

    @Bean
    public NewTopic rideRequestsTopic() {
        return TopicBuilder.name(rideRequestsTopic)
                .partitions(2)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic driverLocationsTopic() {
        return TopicBuilder.name(driverLocationsTopic)
                .partitions(2)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic rideEventsTopic() {
        return TopicBuilder.name(rideEventsTopic)
                .partitions(2)
                .replicas(1)
                .build();
    }
}
