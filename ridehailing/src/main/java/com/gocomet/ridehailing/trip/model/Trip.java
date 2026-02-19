package com.gocomet.ridehailing.trip.model;

import com.gocomet.ridehailing.driver.model.Driver;
import com.gocomet.ridehailing.ride.model.Ride;
import com.gocomet.ridehailing.rider.model.Rider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trips", indexes = {
        @Index(name = "idx_trips_ride_id", columnList = "ride_id", unique = true),
        @Index(name = "idx_trips_driver_status", columnList = "driver_id, status"),
        @Index(name = "idx_trips_rider_id", columnList = "rider_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", nullable = false, unique = true)
    private Ride ride;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rider_id", nullable = false)
    private Rider rider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TripStatus status = TripStatus.IN_PROGRESS;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "start_lat", nullable = false)
    private Double startLat;

    @Column(name = "start_lng", nullable = false)
    private Double startLng;

    @Column(name = "end_lat")
    private Double endLat;

    @Column(name = "end_lng")
    private Double endLng;

    @Column(name = "distance_km", precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "base_fare", precision = 10, scale = 2)
    private BigDecimal baseFare;

    @Column(name = "surge_multiplier", precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal surgeMultiplier = BigDecimal.ONE;

    @Column(name = "total_fare", precision = 10, scale = 2)
    private BigDecimal totalFare;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
