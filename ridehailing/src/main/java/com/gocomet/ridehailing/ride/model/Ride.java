package com.gocomet.ridehailing.ride.model;

import com.gocomet.ridehailing.driver.model.Driver;
import com.gocomet.ridehailing.driver.model.VehicleType;
import com.gocomet.ridehailing.rider.model.Rider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rides", indexes = {
        @Index(name = "idx_rides_rider_id", columnList = "rider_id"),
        @Index(name = "idx_rides_rider_status", columnList = "rider_id, status"),
        @Index(name = "idx_rides_status", columnList = "status"),
        @Index(name = "idx_rides_idempotency", columnList = "idempotency_key", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rider_id", nullable = false)
    private Rider rider;

    @Column(name = "pickup_lat", nullable = false)
    private Double pickupLat;

    @Column(name = "pickup_lng", nullable = false)
    private Double pickupLng;

    @Column(name = "destination_lat", nullable = false)
    private Double destinationLat;

    @Column(name = "destination_lng", nullable = false)
    private Double destinationLng;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_tier", nullable = false)
    private VehicleType vehicleTier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RideStatus status = RideStatus.REQUESTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_driver_id")
    private Driver assignedDriver;

    @Column(name = "surge_multiplier", precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal surgeMultiplier = BigDecimal.ONE;

    @Column(name = "estimated_fare", precision = 10, scale = 2)
    private BigDecimal estimatedFare;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
