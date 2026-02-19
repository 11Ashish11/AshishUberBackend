package com.gocomet.ridehailing.ride.model;

import com.gocomet.ridehailing.driver.model.Driver;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ride_assignments", indexes = {
        @Index(name = "idx_assignments_ride_id", columnList = "ride_id"),
        @Index(name = "idx_assignments_driver_id", columnList = "driver_id"),
        @Index(name = "idx_assignments_ride_driver", columnList = "ride_id, driver_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.OFFERED;

    @CreationTimestamp
    @Column(name = "offered_at", nullable = false, updatable = false)
    private LocalDateTime offeredAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
}
