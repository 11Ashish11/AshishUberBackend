package com.gocomet.ridehailing.driver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private final StringRedisTemplate redisTemplate;

    private static final String DRIVER_LOCATIONS_KEY = "driver:locations";
    private static final String DRIVER_AVAILABLE_PREFIX = "driver:available:";
    private static final String DRIVER_LOCK_PREFIX = "driver:lock:";
    private static final int AVAILABILITY_TTL_SECONDS = 30;

    /**
     * Update driver's location in Redis GEO index.
     * Also refreshes their availability TTL — if a driver stops sending
     * updates, they "disappear" after 30 seconds.
     */
    public void updateDriverLocation(UUID driverId, double lat, double lng, String vehicleType) {
        // Add to GEO set
        redisTemplate.opsForGeo().add(DRIVER_LOCATIONS_KEY,
                new Point(lng, lat),  // Redis GEO uses (longitude, latitude) order!
                driverId.toString());

        // Set availability with TTL (auto-expires if driver goes silent)
        String availabilityKey = DRIVER_AVAILABLE_PREFIX + driverId;
        redisTemplate.opsForValue().set(availabilityKey, vehicleType, AVAILABILITY_TTL_SECONDS, TimeUnit.SECONDS);

        log.debug("Updated location for driver {}: ({}, {})", driverId, lat, lng);
    }

    /**
     * Find nearby available drivers within the given radius.
     * Returns driver IDs sorted by distance (nearest first).
     */
    public List<UUID> findNearbyDrivers(double lat, double lng, double radiusKm, String vehicleType) {
        // Query Redis GEO for drivers within radius
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo()
                .radius(DRIVER_LOCATIONS_KEY,
                        new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS)),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .sortAscending()  // Nearest first
                                .limit(20));       // Don't return thousands

        if (results == null) {
            return new ArrayList<>();
        }

        List<UUID> nearbyDrivers = new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results) {
            String driverIdStr = result.getContent().getName();
            UUID driverId = UUID.fromString(driverIdStr);

            // Check if driver is actually available (TTL key exists)
            String availabilityKey = DRIVER_AVAILABLE_PREFIX + driverIdStr;
            String driverVehicleType = redisTemplate.opsForValue().get(availabilityKey);

            if (driverVehicleType != null && driverVehicleType.equals(vehicleType)) {
                // Check if driver is not locked (already being offered another ride)
                String lockKey = DRIVER_LOCK_PREFIX + driverIdStr;
                if (Boolean.FALSE.equals(redisTemplate.hasKey(lockKey))) {
                    nearbyDrivers.add(driverId);
                }
            }
        }

        log.debug("Found {} nearby available {} drivers near ({}, {})",
                nearbyDrivers.size(), vehicleType, lat, lng);
        return nearbyDrivers;
    }

    /**
     * Lock a driver so they can't be offered another ride simultaneously.
     * Uses Redis SET NX (set if not exists) — acts as a distributed lock.
     * Returns true if lock acquired, false if driver is already locked.
     */
    public boolean lockDriver(UUID driverId, UUID rideId) {
        String lockKey = DRIVER_LOCK_PREFIX + driverId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, rideId.toString(), 20, TimeUnit.SECONDS);
        log.debug("Lock attempt for driver {} on ride {}: {}", driverId, rideId, acquired);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * Release the lock on a driver (after they decline, timeout, or ride is cancelled).
     */
    public void unlockDriver(UUID driverId) {
        String lockKey = DRIVER_LOCK_PREFIX + driverId;
        redisTemplate.delete(lockKey);
        log.debug("Unlocked driver {}", driverId);
    }

    /**
     * Remove driver from the availability pool (when they go offline or start a trip).
     */
    public void removeDriverAvailability(UUID driverId) {
        String availabilityKey = DRIVER_AVAILABLE_PREFIX + driverId;
        redisTemplate.delete(availabilityKey);
        redisTemplate.opsForGeo().remove(DRIVER_LOCATIONS_KEY, driverId.toString());
        log.debug("Removed driver {} from availability pool", driverId);
    }
}
