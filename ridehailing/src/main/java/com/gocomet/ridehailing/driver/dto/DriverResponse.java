package com.gocomet.ridehailing.driver.dto;

import com.gocomet.ridehailing.driver.model.DriverStatus;
import com.gocomet.ridehailing.driver.model.VehicleType;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverResponse {

    private UUID id;
    private String name;
    private VehicleType vehicleType;
    private DriverStatus status;
    private Double currentLat;
    private Double currentLng;
}
