package com.gocomet.ridehailing.common.config;

import com.gocomet.ridehailing.driver.model.Driver;
import com.gocomet.ridehailing.driver.model.DriverStatus;
import com.gocomet.ridehailing.driver.model.VehicleType;
import com.gocomet.ridehailing.driver.repository.DriverRepository;
import com.gocomet.ridehailing.rider.model.Rider;
import com.gocomet.ridehailing.rider.repository.RiderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the database with test data on startup.
 * Uses Bangalore coordinates for realistic testing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final DriverRepository driverRepository;
    private final RiderRepository riderRepository;

    @Override
    public void run(String... args) {
        if (driverRepository.count() > 0) {
            log.info("Database already seeded. Skipping.");
            return;
        }

        log.info("Seeding database with test data...");

        // Create riders
        Rider rider1 = riderRepository.save(Rider.builder()
                .name("Ashish")
                .email("ashish@test.com")
                .phone("+919876543210")
                .build());

        Rider rider2 = riderRepository.save(Rider.builder()
                .name("Priya")
                .email("priya@test.com")
                .phone("+919876543211")
                .build());

        // Create drivers around Bangalore (Koramangala area)
        driverRepository.save(Driver.builder()
                .name("Raju")
                .email("raju@driver.com")
                .phone("+919800000001")
                .vehicleType(VehicleType.SEDAN)
                .status(DriverStatus.AVAILABLE)
                .currentLat(12.9352)  // Koramangala
                .currentLng(77.6245)
                .build());

        driverRepository.save(Driver.builder()
                .name("Kumar")
                .email("kumar@driver.com")
                .phone("+919800000002")
                .vehicleType(VehicleType.AUTO)
                .status(DriverStatus.AVAILABLE)
                .currentLat(12.9279)  // HSR Layout
                .currentLng(77.6271)
                .build());

        driverRepository.save(Driver.builder()
                .name("Suresh")
                .email("suresh@driver.com")
                .phone("+919800000003")
                .vehicleType(VehicleType.SUV)
                .status(DriverStatus.AVAILABLE)
                .currentLat(12.9716)  // MG Road
                .currentLng(77.5946)
                .build());

        driverRepository.save(Driver.builder()
                .name("Venkat")
                .email("venkat@driver.com")
                .phone("+919800000004")
                .vehicleType(VehicleType.SEDAN)
                .status(DriverStatus.AVAILABLE)
                .currentLat(12.9344)  // BTM Layout
                .currentLng(77.6101)
                .build());

        driverRepository.save(Driver.builder()
                .name("Anil")
                .email("anil@driver.com")
                .phone("+919800000005")
                .vehicleType(VehicleType.AUTO)
                .status(DriverStatus.OFFLINE)
                .currentLat(12.9550)  // Indiranagar
                .currentLng(77.6382)
                .build());

        log.info("Seeded {} riders and {} drivers", riderRepository.count(), driverRepository.count());
    }
}
