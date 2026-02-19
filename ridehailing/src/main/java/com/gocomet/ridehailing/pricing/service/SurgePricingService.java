package com.gocomet.ridehailing.pricing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SurgePricingService {

    private final StringRedisTemplate redisTemplate;

    private static final String SURGE_PREFIX = "surge:";
    private static final int SURGE_TTL_SECONDS = 60;

    // Base fare per km for each tier
    private static final BigDecimal AUTO_BASE_PER_KM = new BigDecimal("8.00");
    private static final BigDecimal SEDAN_BASE_PER_KM = new BigDecimal("12.00");
    private static final BigDecimal SUV_BASE_PER_KM = new BigDecimal("18.00");

    private static final BigDecimal MINIMUM_FARE = new BigDecimal("30.00");

    /**
     * Calculate surge multiplier based on demand in the area.
     * Uses a simplified geo-hash approach — divides the world into grid cells
     * and tracks demand per cell.
     */
    public BigDecimal getSurgeMultiplier(double lat, double lng) {
        String geoHash = getSimpleGeoHash(lat, lng);
        String surgeKey = SURGE_PREFIX + geoHash;

        String cachedSurge = redisTemplate.opsForValue().get(surgeKey);
        if (cachedSurge != null) {
            return new BigDecimal(cachedSurge);
        }

        // Calculate based on recent demand (simplified)
        // In production, this would look at request count vs available drivers
        BigDecimal surge = BigDecimal.ONE;

        // Cache it
        redisTemplate.opsForValue().set(surgeKey, surge.toString(), SURGE_TTL_SECONDS, TimeUnit.SECONDS);

        return surge;
    }

    /**
     * Record demand in an area (call this when a ride is requested).
     * Increments a counter that the surge calculation uses.
     */
    public void recordDemand(double lat, double lng) {
        String geoHash = getSimpleGeoHash(lat, lng);
        String demandKey = "demand:" + geoHash;
        redisTemplate.opsForValue().increment(demandKey);
        redisTemplate.expire(demandKey, 5, TimeUnit.MINUTES);

        // Recalculate surge for this area
        recalculateSurge(geoHash, demandKey);
    }

    private void recalculateSurge(String geoHash, String demandKey) {
        String countStr = redisTemplate.opsForValue().get(demandKey);
        long demandCount = countStr != null ? Long.parseLong(countStr) : 0;

        BigDecimal surge;
        if (demandCount > 20) {
            surge = new BigDecimal("2.0");
        } else if (demandCount > 10) {
            surge = new BigDecimal("1.5");
        } else if (demandCount > 5) {
            surge = new BigDecimal("1.2");
        } else {
            surge = BigDecimal.ONE;
        }

        String surgeKey = SURGE_PREFIX + geoHash;
        redisTemplate.opsForValue().set(surgeKey, surge.toString(), SURGE_TTL_SECONDS, TimeUnit.SECONDS);

        log.debug("Surge for area {}: {} (demand: {})", geoHash, surge, demandCount);
    }

    /**
     * Estimate fare based on distance, tier, and surge.
     */
    public BigDecimal estimateFare(double pickupLat, double pickupLng,
                                    double destLat, double destLng,
                                    String vehicleTier, BigDecimal surgeMultiplier) {
        double distanceKm = calculateDistance(pickupLat, pickupLng, destLat, destLng);

        BigDecimal baseFarePerKm = switch (vehicleTier) {
            case "AUTO" -> AUTO_BASE_PER_KM;
            case "SUV" -> SUV_BASE_PER_KM;
            default -> SEDAN_BASE_PER_KM;
        };

        BigDecimal fare = baseFarePerKm
                .multiply(BigDecimal.valueOf(distanceKm))
                .multiply(surgeMultiplier);

        // Apply minimum fare
        return fare.compareTo(MINIMUM_FARE) < 0 ? MINIMUM_FARE : fare.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Calculate distance between two points using Haversine formula.
     */
    public double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371; // Earth's radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * Simple geo-hash: divides world into ~1km grid cells.
     */
    private String getSimpleGeoHash(double lat, double lng) {
        int latCell = (int) (lat * 100);
        int lngCell = (int) (lng * 100);
        return latCell + ":" + lngCell;
    }
}
