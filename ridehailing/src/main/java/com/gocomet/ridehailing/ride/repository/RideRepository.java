package com.gocomet.ridehailing.ride.repository;

import com.gocomet.ridehailing.ride.model.Ride;
import com.gocomet.ridehailing.ride.model.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RideRepository extends JpaRepository<Ride, UUID> {
    Optional<Ride> findByIdempotencyKey(String idempotencyKey);
    List<Ride> findByRiderIdAndStatusIn(UUID riderId, List<RideStatus> statuses);
    boolean existsByRiderIdAndStatusIn(UUID riderId, List<RideStatus> statuses);
}
