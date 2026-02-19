package com.gocomet.ridehailing.driver.repository;

import com.gocomet.ridehailing.driver.model.Driver;
import com.gocomet.ridehailing.driver.model.DriverStatus;
import com.gocomet.ridehailing.driver.model.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {
    Optional<Driver> findByEmail(String email);
    List<Driver> findByStatusAndVehicleType(DriverStatus status, VehicleType vehicleType);
    List<Driver> findByStatus(DriverStatus status);
}
