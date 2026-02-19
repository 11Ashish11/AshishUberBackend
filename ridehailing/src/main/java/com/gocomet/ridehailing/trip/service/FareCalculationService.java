package com.gocomet.ridehailing.trip.service;

import com.gocomet.ridehailing.pricing.service.SurgePricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class FareCalculationService {

    private final SurgePricingService surgePricingService;

    /**
     * Calculate the final fare for a completed trip.
     */
    public BigDecimal calculateFare(double startLat, double startLng,
                                     double endLat, double endLng,
                                     String vehicleTier, BigDecimal surgeMultiplier) {
        double distanceKm = surgePricingService.calculateDistance(startLat, startLng, endLat, endLng);
        return surgePricingService.estimateFare(startLat, startLng, endLat, endLng, vehicleTier, surgeMultiplier);
    }

    /**
     * Calculate distance for the trip record.
     */
    public BigDecimal calculateDistanceKm(double startLat, double startLng,
                                           double endLat, double endLng) {
        double distance = surgePricingService.calculateDistance(startLat, startLng, endLat, endLng);
        return BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP);
    }
}
