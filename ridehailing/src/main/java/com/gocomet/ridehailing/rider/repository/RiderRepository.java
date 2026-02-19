package com.gocomet.ridehailing.rider.repository;

import com.gocomet.ridehailing.rider.model.Rider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiderRepository extends JpaRepository<Rider, UUID> {
    Optional<Rider> findByEmail(String email);
    Optional<Rider> findByPhone(String phone);
    boolean existsByEmail(String email);
}
