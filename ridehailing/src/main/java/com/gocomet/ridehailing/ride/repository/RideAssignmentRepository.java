package com.gocomet.ridehailing.ride.repository;

import com.gocomet.ridehailing.ride.model.AssignmentStatus;
import com.gocomet.ridehailing.ride.model.RideAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RideAssignmentRepository extends JpaRepository<RideAssignment, UUID> {
    List<RideAssignment> findByRideId(UUID rideId);
    Optional<RideAssignment> findByRideIdAndDriverId(UUID rideId, UUID driverId);
    Optional<RideAssignment> findByRideIdAndStatus(UUID rideId, AssignmentStatus status);
    boolean existsByRideIdAndDriverId(UUID rideId, UUID driverId);
    List<RideAssignment> findByDriverIdAndStatus(UUID driverId, AssignmentStatus status);
}
