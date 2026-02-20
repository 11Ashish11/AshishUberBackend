package com.gocomet.ridehailing.config;

import com.gocomet.ridehailing.driver.model.VehicleType;
import com.gocomet.ridehailing.payment.model.PaymentMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/v1/config")
public class ConfigController {

    /**
     * GET /v1/config/vehicle-tiers — List supported vehicle tiers.
     */
    @GetMapping("/vehicle-tiers")
    public ResponseEntity<List<String>> getVehicleTiers() {
        List<String> tiers = Arrays.stream(VehicleType.values())
                .map(Enum::name)
                .toList();
        return ResponseEntity.ok(tiers);
    }

    /**
     * GET /v1/config/payment-methods — List supported payment methods.
     */
    @GetMapping("/payment-methods")
    public ResponseEntity<List<String>> getPaymentMethods() {
        List<String> methods = Arrays.stream(PaymentMethod.values())
                .map(Enum::name)
                .toList();
        return ResponseEntity.ok(methods);
    }
}

