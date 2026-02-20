package com.gocomet.ridehailing.rider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiderResponse {

    private UUID id;
    private String name;
    private String email;
    private String phone;
    private LocalDateTime createdAt;
}

