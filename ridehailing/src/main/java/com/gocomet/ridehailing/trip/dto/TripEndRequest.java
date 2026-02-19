package com.gocomet.ridehailing.trip.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripEndRequest {

    @NotNull(message = "End latitude is required")
    private Double endLat;

    @NotNull(message = "End longitude is required")
    private Double endLng;
}
