# GoComet Ride-Hailing Platform

A multi-tenant ride-hailing system (Uber/Ola clone) built as part of the GoComet SDE-2 assignment. Supports real-time driver-rider matching, dynamic surge pricing, trip lifecycle management, payment processing, and **event-driven architecture via Apache Kafka**.

## Requirements Coverage

| Requirement | Status | What's Done | What's Pending |
|-------------|--------|-------------|----------------|
| **Real-time driver location ingestion** (1–2 updates/sec) | Partial | `POST /v1/drivers/{id}/location` updates Redis GEO + Postgres; 30s TTL auto-expires stale drivers; WebSocket broadcasts location to frontend | No server-side rate enforcement of 1–2 updates/sec cadence; no driver-side push — purely request-driven (driver must call the API) |
| **Ride request flow** (pickup, destination, tier, payment method) | Done | `POST /v1/rides` accepts all fields: pickup/destination coords, vehicleTier, paymentMethod, riderId; idempotency keys supported; active ride check prevents double-booking | — |
| **Dispatch/Matching** (<1s p95, reassign on decline/timeout) | Partial | Redis GEOSEARCH for nearest driver (microsecond queries); distributed lock (SET NX) prevents double-assignment; reassign on decline works — marks DECLINED, unlocks driver, tries next | No timeout-based reassignment — if driver doesn't respond, no scheduler fires to reassign; Redis lock TTL expires silently with no follow-up action; no p95 measurement or enforcement |
| **Dynamic surge pricing** (per geo-cell, supply–demand) | Partial | Demand counted per geohash cell; surge tiers: 1.0×/1.2×/1.5×/2.0×; applied at ride creation and fare calculation; cached in Redis with TTL | Supply side not factored in — only raw demand count used, not demand/supply ratio; no driver-count-per-cell signal |
| **Trip lifecycle** (start, pause, end, fare calculation, receipts) | Partial | Trip auto-created on driver accept; `POST /v1/trips/{id}/end` with Haversine fare calc; surge multiplier applied; driver re-added to pool on completion | No PAUSE/RESUME state; no receipt generation (email/PDF); state machine is `IN_PROGRESS → COMPLETED` only |
| **Payments orchestration** (PSP integration, retries, reconciliation) | Partial | PSP stub simulating Razorpay/Stripe (90% success, random 200–1500ms latency); idempotency keys; status tracking PENDING → PROCESSING → SUCCESS/FAILED | Stub only — no real PSP integration; no retry logic on FAILED payments; no reconciliation job or reporting |
| **Notifications** (push/SMS for key ride states) | Partial | WebSocket/STOMP push for ride offer, acceptance, fare details on trip end, payment result | No SMS (no Twilio/SNS); no FCM/APNS mobile push; in-app WebSocket only |
| **Admin/ops tooling** (feature flags, kill-switches, observability) | Not Done | — | No feature flag system; no kill-switches or circuit breakers; no admin endpoints; New Relic mentioned in README but not integrated in code |


## Tech Stack

| Component | Technology |
|-----------|-----------|
| Backend | Java 17, Spring Boot 4.0.3 |
| Database | PostgreSQL 16 |
| Cache/Geo | Redis 7 (GEO index, distributed locks) |
| Event Streaming | Apache Kafka 3.9 (KRaft mode — no ZooKeeper) |
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
       |                    |              |
       v                    v              v
  [PostgreSQL 16]      [Redis 7]     [Kafka 3.9]
  (durable state)   (geo, locks)   (event stream)
```

**Kafka Topics:**
```
ride-events      ← all ride state transitions (key=rideId)
driver-locations ← high-frequency GPS pings  (key=driverId)
ride-requests    ← rider booking commands     (key=requestId)
```

**Why Modular Monolith?** Clean separation of concerns with the operational simplicity of a single deployment. Each module has its own controller, service, repository, and model layers. In production, these can be independently deployed as microservices.

## Core Features

- **Real-time Driver Matching** — Redis GEOSEARCH finds nearest available drivers within 5km in microseconds
- **Distributed Locking** — Redis SET NX prevents double-assignment of drivers
- **Dynamic Surge Pricing** — Demand-based multiplier per geo-cell (1km grid)
- **Trip Lifecycle** — Clean state machine: REQUESTED → MATCHING → MATCHED → ACCEPTED → IN_PROGRESS → COMPLETED
- **Ride Cancellation** — Cancel rides before trip starts (REQUESTED, MATCHING, MATCHED statuses only)
- **Payment via PSP Stub** — Simulates Razorpay/Stripe with 90% success rate, random latency, and idempotency
- **Idempotent APIs** — Both ride creation and payments support idempotency keys to prevent duplicates
- **WebSocket Live Updates** — STOMP over WebSocket pushes ride status and driver locations to frontend
- **Haversine Distance** — Accurate fare calculation using great-circle distance formula
- **Kafka Event Streaming** — Every ride state transition and driver GPS update is published to Kafka for downstream consumers (analytics, billing, notifications)

## Project Structure

```
src/main/java/com/gocomet/ridehailing/
├── common/                  # Shared config, exceptions, utilities
│   ├── config/              # Redis, WebSocket, CORS, DataSeeder, KafkaConfig
│   ├── event/               # RideEvent (shared Kafka event model)
│   └── exception/           # Global exception handler
├── driver/                  # Driver profiles, location ingestion
│   ├── controller/          # POST /v1/drivers/{id}/location, /accept, /online, /offline
│   ├── event/               # DriverLocationProducer, DriverLocationConsumer
│   ├── service/             # DriverService, LocationService (Redis GEO)
│   ├── model/               # Driver entity, DriverStatus, VehicleType
│   └── repository/
├── ride/                    # Ride requests and matching
│   ├── controller/          # POST /v1/rides, GET /v1/rides/{id}
│   ├── event/               # RideEventProducer, RideEventConsumer
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
| GET | `/v1/riders` | List all riders (for frontend/demo) |
| GET | `/v1/drivers` | List all drivers (for frontend/demo) |
| GET | `/v1/config/vehicle-tiers` | List supported vehicle tiers |
| GET | `/v1/config/payment-methods` | List supported payment methods |
| POST | `/v1/rides` | Create ride request |
| GET | `/v1/rides/{id}` | Get ride status |
| POST | `/v1/rides/{id}/cancel` | Cancel a ride (only REQUESTED, MATCHING, MATCHED statuses) |
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

This starts:
- PostgreSQL on port **5433**
- Redis on port **6379**
- Kafka (KRaft) on port **9092**

### 2. Start Backend

```bash
cd ridehailing
./gradlew bootRun
```

The app starts on `http://localhost:8080`. On first run, it seeds the database with 2 test riders and 5 test drivers around Bangalore. Kafka topics are auto-created on first message.

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

# Cancel a ride (before trip starts)
curl -X POST http://localhost:8080/v1/rides/{RIDE_ID}/cancel

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

## Kafka Event Flow

Every key action in the ride lifecycle automatically publishes a message to Kafka:

| Trigger | Topic | Event Type | Key |
|---|---|---|---|
| Rider creates a ride | `ride-events` | `REQUESTED` | `rideId` |
| Driver accepts | `ride-events` | `DRIVER_ASSIGNED` | `rideId` |
| Trip created | `ride-events` | `TRIP_STARTED` | `rideId` |
| Ride cancelled | `ride-events` | `CANCELLED` | `rideId` |
| Driver sends GPS | `driver-locations` | GPS payload | `driverId` |

**Consumer groups:**
- `ride-state-tracker` — consumes `ride-events`, logs all state transitions
- `driver-location-tracker` — consumes `driver-locations`, logs GPS updates

All ride events are **keyed by `rideId`** so events for the same ride always land in the same partition, guaranteeing ordered processing.

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

## Ride Cancellation Logic

Riders can cancel rides before the trip officially starts. The cancellation flow:

**Cancellable Statuses:**
- `REQUESTED` - Ride just created, matching in progress
- `MATCHING` - System is searching for drivers
- `MATCHED` - Driver found and offered the ride

**Non-Cancellable Statuses:**
- `ACCEPTED` - Driver accepted, trip about to start/started (returns 400 error)
- `IN_PROGRESS` - Trip already started (returns 400 error)
- `COMPLETED` - Trip finished (returns 400 error)
- `CANCELLED` - Already cancelled (returns 400 error)

**Cancellation Actions:**
1. Validates ride exists and is in cancellable status
2. If a driver was matched/assigned, releases the distributed lock on that driver
3. Updates ride status to `CANCELLED`
4. Returns updated ride response

**Handling "Active Ride" Errors:**
If you get a `409 Conflict` error saying "Rider already has an active ride", it means there's a stale ride from a previous session. Solutions:

1. **Via API** - Find the active ride ID and cancel it:
   ```bash
   # Get rider's rides from database
   docker exec -it ridehailing-db psql -U admin -d ridehailing \
     -c "SELECT id, status FROM rides WHERE rider_id = '{RIDER_ID}' ORDER BY created_at DESC;"

   # Cancel the active ride
   curl -X POST http://localhost:8080/v1/rides/{RIDE_ID}/cancel
   ```

2. **Via Database** - Directly update stale rides:
   ```sql
   UPDATE rides
   SET status = 'CANCELLED'
   WHERE rider_id = '{RIDER_ID}'
   AND status IN ('REQUESTED', 'MATCHING', 'MATCHED', 'ACCEPTED');
   ```
## Documentation

- [HLD/LLD Document](./docs/GoComet_RideHailing_HLD_LLD.docx) — Comprehensive architecture document with diagrams, schema, state machines, and design decisions
