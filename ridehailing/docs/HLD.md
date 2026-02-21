# HLD — High-Level Design

## System Overview

A multi-tenant ride-hailing platform handling driver–rider matching, surge pricing, trip lifecycle, payments, and real-time notifications. Built as a **modular monolith** (deployable as microservices) with Kafka for event streaming, Redis for hot KV and geo-search, and Postgres for durable state.

---

## Components

```
┌──────────────────────────────────────────────────────────────┐
│                     Client Layer                             │
│         Rider App (React)        Driver App (React)          │
│         HTTP REST + WebSocket    HTTP REST + WebSocket       │
└─────────────────────┬──────────────────────┬────────────────┘
                      │                      │
                      ▼                      ▼
┌──────────────────────────────────────────────────────────────┐
│                Spring Boot Application                       │
│                  (Modular Monolith)                          │
│                                                              │
│  [Ride]    [Driver]    [Trip]    [Payment]                   │
│  [Pricing] [Notification]       [Config]                     │
└──────┬──────────┬───────────────────┬────────────────────────┘
       │          │                   │
       ▼          ▼                   ▼
 [PostgreSQL 16] [Redis 7]     [Kafka 3.9 KRaft]
 (durable state) (geo + locks) (event streaming)
```

### Module Breakdown

| Module | Responsibility |
|--------|---------------|
| **Ride** | Ride creation, matching orchestration, state transitions |
| **Driver** | Driver profiles, location ingestion, Redis geo-index |
| **Trip** | Trip lifecycle, Haversine fare calculation |
| **Payment** | PSP integration, idempotency, status tracking |
| **Pricing** | Surge multiplier per geo-cell, demand tracking |
| **Notification** | WebSocket/STOMP push to riders and drivers |

---

## Data Flow

### Ride Request Flow
```
Rider → POST /v1/rides
  → RideService validates (no active ride, idempotency key)
  → SurgePricingService.recordDemand() — increments Redis demand counter
  → SurgePricingService.getSurgeMultiplier() — calculates fare estimate
  → Ride saved to Postgres (status: REQUESTED)
  → RideEventProducer publishes REQUESTED event → ride-events topic
  → MatchingService.findAndAssignDriver()
      → LocationService.findNearbyDrivers() — Redis GEOSEARCH
      → LocationService.lockDriver() — Redis SET NX (distributed lock)
      → RideAssignment saved, ride status → MATCHED
      → NotificationService.notifyDriver() — WebSocket RIDE_OFFER
      → NotificationService.notifyRider() — WebSocket DRIVER_MATCHED
```

### Driver Location Flow
```
Driver → POST /v1/drivers/{id}/location
  → DriverService.updateLocation() — Postgres update
  → LocationService.updateDriverLocation() — Redis GEO + availability TTL
  → DriverLocationProducer publishes GPS event → driver-locations topic
  → NotificationService.broadcastDriverLocation() — WebSocket /topic/locations
```

### Trip Completion Flow
```
Driver → POST /v1/trips/{id}/end
  → TripService validates IN_PROGRESS
  → FareCalculationService.calculateDistanceKm() — Haversine formula
  → Fare = baseFarePerKm × distance × surgeMultiplier
  → Driver status → AVAILABLE, re-added to Redis geo pool
  → NotificationService.notifyRider() — TRIP_COMPLETED with fare
  → POST /v1/payments — PSP stub processes charge
```

---

## Kafka Topics

| Topic | Key | Partitions | Consumers |
|-------|-----|------------|-----------|
| `ride-events` | `rideId` | 2 | `ride-state-tracker` |
| `driver-locations` | `driverId` | 2 | `driver-location-tracker` |
| `ride-requests` | `requestId` | 2 | (extensible) |

**Why keyed by rideId/driverId?** All events for the same ride/driver land in the same partition → guaranteed ordering without coordination.

---

## Storage Decisions

| Store | Used For | Why |
|-------|----------|-----|
| **Postgres** | Rides, trips, drivers, payments, riders | ACID transactions, relational joins, audit trail |
| **Redis GEO** | Driver location index | GEOSEARCH in microseconds — O(N+log(M)) |
| **Redis SET NX** | Driver assignment lock | Atomic compare-and-set prevents double-assignment |
| **Redis INCR** | Demand counter per geo-cell | Lock-free atomic counter for surge calculation |
| **Redis TTL** | Driver availability (30s), surge cache (60s) | Auto-expiry removes stale data without cleanup jobs |
| **Kafka** | All state change events | Durable, replayable, decoupled consumers |

---

## Scaling Strategy

### Location Ingestion (500k updates/sec globally)
- Kafka `driver-locations` topic partitioned by `driverId` — horizontal fan-out
- Redis GEO is in-memory — sub-millisecond writes
- Postgres location update is async via Kafka consumer (not on hot path in production)

### Matching (<1s p95)
- Redis GEOSEARCH is O(N+log(M)) — typically <5ms for 300k drivers
- Lock acquisition is a single Redis round-trip (~1ms)
- All matching happens in memory (Redis) — no Postgres queries on the critical path

### Multi-Region (Designed for, not implemented)
- Region-local writes to Kafka and Redis
- Postgres with CockroachDB for cross-region replication
- No cross-region sync on hot path — riders match with local drivers only

---

## Trade-offs

| Decision | Chosen | Alternative | Reason |
|----------|--------|-------------|--------|
| Architecture | Modular monolith | Microservices | Simpler ops for assignment; clean module boundaries allow future extraction |
| Matching store | Redis GEO | PostGIS | Redis is in-memory (~1ms); PostGIS adds DB load on hot path |
| Kafka mode | KRaft (no ZooKeeper) | Zookeeper | Fewer moving parts; ZooKeeper deprecated in Kafka 4.x |
| Surge calculation | Demand-only | Supply/demand ratio | Simpler to implement; supply-side adds driver tracking complexity |
| PSP | Stub (90% success sim) | Real Razorpay/Stripe | Out of scope for assignment; interface is real and swappable |
