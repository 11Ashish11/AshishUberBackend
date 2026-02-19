# GoComet Ride-Hailing Platform

A multi-tenant ride-hailing system (Uber/Ola clone) built as part of the GoComet SDE-2 assignment. Supports real-time driver-rider matching, dynamic surge pricing, trip lifecycle management, and payment processing.

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Backend | Java 17, Spring Boot 4.0.3 |
| Database | PostgreSQL 16 |
| Cache/Geo | Redis 7 (GEO index, distributed locks) |
| Frontend | React (with WebSocket live updates) |
| Containerization | Docker Compose |
| Monitoring | New Relic APM |

## Architecture

```
  Rider App (React)              Driver App (React)
       |                              |
       | HTTP REST                    | HTTP REST
       | + WebSocket                  | + WebSocket
       v                              v
  +-------------------------------------------+
  |       Spring Boot Application             |
  |  (Modular Monolith - 6 modules)          |
  |                                           |
  |  [Ride]  [Driver]  [Trip]                |
  |  [Payment]  [Pricing]  [Notification]    |
  +-------------------------------------------+
       |                    |
       v                    v
  [PostgreSQL 16]      [Redis 7]
  (durable state)      (geo, locks, cache)
```

**Why Modular Monolith?** Clean separation of concerns with the operational simplicity of a single deployment. Each module has its own controller, service, repository, and model layers. In production, these can be independently deployed as microservices.

## Core Features

- **Real-time Driver Matching** — Redis GEOSEARCH finds nearest available drivers within 5km in microseconds
- **Distributed Locking** — Redis SET NX prevents double-assignment of drivers
- **Dynamic Surge Pricing** — Demand-based multiplier per geo-cell (1km grid)
- **Trip Lifecycle** — Clean state machine: REQUESTED → MATCHING → MATCHED → ACCEPTED → IN_PROGRESS → COMPLETED
- **Payment via PSP Stub** — Simulates Razorpay/Stripe with 90% success rate, random latency, and idempotency
- **Idempotent APIs** — Both ride creation and payments support idempotency keys to prevent duplicates
- **WebSocket Live Updates** — STOMP over WebSocket pushes ride status and driver locations to frontend
- **Haversine Distance** — Accurate fare calculation using great-circle distance formula

## Project Structure

```
src/main/java/com/gocomet/ridehailing/
├── common/                  # Shared config, exceptions, utilities
│   ├── config/              # Redis, WebSocket, CORS, DataSeeder
│   └── exception/           # Global exception handler
├── driver/                  # Driver profiles, location ingestion
│   ├── controller/          # POST /v1/drivers/{id}/location, /accept, /online, /offline
│   ├── service/             # DriverService, LocationService (Redis GEO)
│   ├── model/               # Driver entity, DriverStatus, VehicleType
│   └── repository/
├── ride/                    # Ride requests and matching
│   ├── controller/          # POST /v1/rides, GET /v1/rides/{id}
│   ├── service/             # RideService, MatchingService (the brain)
│   ├── model/               # Ride, RideAssignment, RideStatus, AssignmentStatus
│   └── repository/
├── trip/                    # Trip lifecycle and fare calculation
│   ├── controller/          # POST /v1/trips/{id}/end
│   ├── service/             # TripService, FareCalculationService
│   └── model/               # Trip, TripStatus
├── payment/                 # Payment orchestration
│   ├── controller/          # POST /v1/payments
│   ├── service/             # PaymentService, PspStubService
│   └── model/               # Payment, PaymentStatus, PaymentMethod
├── pricing/                 # Surge pricing
│   └── service/             # SurgePricingService
└── notification/            # WebSocket push notifications
    └── service/             # NotificationService
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/rides` | Create ride request |
| GET | `/v1/rides/{id}` | Get ride status |
| POST | `/v1/rides/{id}/cancel` | Cancel a ride |
| POST | `/v1/drivers/{id}/location` | Send driver location update |
| POST | `/v1/drivers/{id}/accept?rideId=` | Accept ride assignment |
| POST | `/v1/drivers/{id}/decline?rideId=` | Decline ride assignment |
| POST | `/v1/drivers/{id}/online` | Go online (join matching pool) |
| POST | `/v1/drivers/{id}/offline` | Go offline |
| POST | `/v1/trips/{id}/end` | End trip and calculate fare |
| GET | `/v1/trips/{id}` | Get trip details |
| POST | `/v1/payments` | Trigger payment flow |

## Quick Start

### Prerequisites
- Java 17
- Docker Desktop
- Node.js (for frontend)

### 1. Start Infrastructure

```bash
# From project root (where docker-compose.yml is)
docker compose up -d
```

This starts PostgreSQL (port 5433) and Redis (port 6379).

### 2. Start Backend

```bash
cd ridehailing
./gradlew bootRun
```

The app starts on `http://localhost:8080`. On first run, it seeds the database with 2 test riders and 5 test drivers around Bangalore.

### 3. Test the Full Ride Flow

```bash
# Get rider and driver IDs
docker exec -it ridehailing-db psql -U admin -d ridehailing \
  -c "SELECT id, name FROM riders;"
docker exec -it ridehailing-db psql -U admin -d ridehailing \
  -c "SELECT id, name, status, vehicle_type FROM drivers;"

# Put a driver online
curl -X POST http://localhost:8080/v1/drivers/{DRIVER_ID}/online

# Send driver location
curl -X POST http://localhost:8080/v1/drivers/{DRIVER_ID}/location \
  -H "Content-Type: application/json" \
  -d '{"latitude": 12.9352, "longitude": 77.6245}'

# Create a ride
curl -X POST http://localhost:8080/v1/rides \
  -H "Content-Type: application/json" \
  -d '{
    "riderId": "{RIDER_ID}",
    "pickupLat": 12.9340,
    "pickupLng": 77.6200,
    "destinationLat": 12.9716,
    "destinationLng": 77.5946,
    "vehicleTier": "SEDAN",
    "paymentMethod": "UPI",
    "idempotencyKey": "ride-001"
  }'

# Driver accepts
curl -X POST "http://localhost:8080/v1/drivers/{DRIVER_ID}/accept?rideId={RIDE_ID}"

# End trip
curl -X POST http://localhost:8080/v1/trips/{TRIP_ID}/end \
  -H "Content-Type: application/json" \
  -d '{"endLat": 12.9716, "endLng": 77.5946}'

# Process payment
curl -X POST http://localhost:8080/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "tripId": "{TRIP_ID}",
    "paymentMethod": "UPI",
    "idempotencyKey": "pay-001"
  }'
```

## Database Schema

6 tables: `riders`, `drivers`, `rides`, `ride_assignments`, `trips`, `payments`

Key design decisions:
- **rides vs trips separation** — A ride is the request; a trip is the actual journey. Not every ride becomes a trip.
- **ride_assignments table** — Tracks the full chain of driver offers (offered → declined → offered → accepted). Enables debugging and fairness analysis.
- **Idempotency keys** — On both rides and payments to prevent duplicates from network retries.

## Redis Usage

| Key | Purpose |
|-----|---------|
| `driver:locations` (GEO) | Spatial index for GEOSEARCH — find nearest drivers in microseconds |
| `driver:available:{id}` (TTL 30s) | Driver availability — auto-expires if driver stops sending updates |
| `driver:lock:{id}` (SET NX, TTL 20s) | Distributed lock — prevents double-assignment |
| `surge:{geohash}` (TTL 60s) | Cached surge multiplier per area |
| `demand:{geohash}` (TTL 5min) | Request counter for surge calculation |

## Concurrency Handling

| Race Condition | Solution |
|---------------|----------|
| Two rides offered to same driver | Redis SET NX distributed lock |
| Rider creates duplicate rides | Idempotency key + active ride check |
| Double payment for same trip | Idempotency key + DB unique constraint |
| Driver accepts expired offer | Assignment status must be OFFERED |
| Stale driver in pool | TTL auto-expiry on availability keys |

## Documentation

- [HLD/LLD Document](./docs/GoComet_RideHailing_HLD_LLD.docx) — Comprehensive architecture document with diagrams, schema, state machines, and design decisions
