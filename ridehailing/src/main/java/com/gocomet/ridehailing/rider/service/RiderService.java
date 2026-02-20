package com.gocomet.ridehailing.rider.service;

import com.gocomet.ridehailing.rider.dto.RiderResponse;
import com.gocomet.ridehailing.rider.model.Rider;
import com.gocomet.ridehailing.rider.repository.RiderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RiderService {

    private final RiderRepository riderRepository;

    public List<RiderResponse> getAllRiders() {
        return riderRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private RiderResponse toResponse(Rider rider) {
        return RiderResponse.builder()
                .id(rider.getId())
                .name(rider.getName())
                .email(rider.getEmail())
                .phone(rider.getPhone())
                .createdAt(rider.getCreatedAt())
                .build();
    }
}

