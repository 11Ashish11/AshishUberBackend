package com.gocomet.ridehailing.trip.repository;

import com.gocomet.ridehailing.trip.model.Trip;
import com.gocomet.ridehailing.trip.model.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {
    Optional<Trip> findByRideId(UUID rideId);
    Optional<Trip> findByDriverIdAndStatus(UUID driverId, TripStatus status);
    boolean existsByDriverIdAndStatus(UUID driverId, TripStatus status);
}
