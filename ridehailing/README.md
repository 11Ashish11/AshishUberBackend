# GoComet Ride-Hailing Platform

A multi-tenant ride-hailing system (Uber/Ola clone) built as part of the GoComet SDE-2 assignment. Supports real-time driver-rider matching, dynamic surge pricing, trip lifecycle management, payment processing, and event-driven architecture via Apache Kafka.

> 📋 **[End-to-End Testing Guide](./docs/TESTING.md)** — Step-by-step setup and demo walkthrough for reviewers

---

## Demo

https://github.com/user-attachments/assets/BackendDemoAPI.mov

> ⚠️ **Note:** The video above will render as an inline player once pushed to GitHub. If viewing locally, open [`docs/BackendDemoAPI.mov`](./docs/BackendDemoAPI.mov) directly.

---

## Deliverables

| Document | Description |
|----------|-------------|
| [HLD — High-Level Design](./docs/HLD.md) | Components, data flow, scaling strategy, storage decisions, trade-offs |
| [LLD — Dispatch/Matching Deep Dive](./docs/LLD.md) | Algorithm, Redis data structures, race conditions, latency budget |
| [APIs & Events](./docs/API_EVENTS.md) | REST request/response schemas, Kafka event schemas, WebSocket channels |
| [Data Model](./docs/DATA_MODEL.md) | ERD and full table schemas for all 6 tables with design rationale |
| [Resilience Plan](./docs/RESILIENCE.md) | Idempotency, locking, TTLs, Kafka error handling, failure mode analysis, production gaps |

---

## Requirements Coverage

| Requirement | Status | What's Done | What's Pending |
|-------------|--------|-------------|----------------|
| **Real-time driver location ingestion** | ✅ Done | `POST /v1/drivers/{id}/location` updates Redis GEO index + Postgres; 30s TTL auto-expires stale drivers; WebSocket broadcasts location to frontend map; every GPS ping published to `driver-locations` Kafka topic | No server-side rate enforcement of 1–2 updates/sec cadence; no driver-side push — purely request-driven |
| **Ride request flow** | ✅ Done | `POST /v1/rides` accepts pickup/destination coords, vehicleTier, paymentMethod, riderId; idempotency keys prevent duplicates; active ride check prevents double-booking; ride event published to `ride-requests` Kafka topic | — |
| **Dispatch/Matching** | ✅ Done | Redis GEOSEARCH finds nearest driver (microsecond queries); distributed lock (SET NX) prevents double-assignment; reassign on driver decline — marks DECLINED, unlocks driver, tries next; full `REQUESTED → MATCHING → MATCHED → ACCEPTED` state machine | No timeout-based reassignment — if driver goes silent, no scheduler fires to retry; no p95 SLA measurement |
| **Dynamic surge pricing** | Partial | Demand tracked per geohash cell (1km grid) in Redis with 5min TTL; surge tiers: 1.0×/1.2×/1.5×/2.0× based on demand count; cached per area with 60s TTL; applied at ride creation and fare calculation | Supply side not factored in — only raw demand count, not demand/supply ratio |
| **Trip lifecycle** | ✅ Done | Trip auto-created on driver accept; `POST /v1/trips/{id}/end` with Haversine distance + tiered fare calculation; surge multiplier applied to final fare; driver re-added to Redis pool on completion; `TRIP_COMPLETED` + `TRIP_STARTED` Kafka events published | No PAUSE/RESUME state; no receipt generation (email/PDF) |
| **Kafka event streaming** | ✅ Done | KRaft-mode Kafka (no ZooKeeper); 3 topics: `ride-events`, `driver-locations`, `ride-requests`; producers publish on every state change; `ErrorHandlingDeserializer` for fault-tolerant consumers | — |
| **Payments orchestration** | Partial | PSP stub simulating Razorpay/Stripe (90% success, 200–1500ms latency); idempotency keys; full lifecycle: `PENDING → PROCESSING → SUCCESS/FAILED`; rider notified via WebSocket | Stub only — no real PSP SDK; no automatic retry on FAILED; no reconciliation job |
| **Notifications** | Partial | WebSocket/STOMP push for: ride offer to driver, driver matched to rider, trip fare on completion, payment result | No SMS (no Twilio); no mobile push (no FCM/APNS) |
| **Admin/ops tooling** | ❌ Not Done | — | No feature flags; no kill-switches; no circuit breakers; no admin endpoints |

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Backend | Java 17, Spring Boot 3.4.2 |
| Database | PostgreSQL 16 |
| Cache/Geo | Redis 7 (GEO index, distributed locks) |
| Event Streaming | Apache Kafka 3.9 (KRaft mode — no ZooKeeper) |
| Frontend | React (with WebSocket live updates) |
| Containerization | Docker Compose |

---

## Quick Start

### Prerequisites
- Java 17+
- Docker Desktop

### 1. Start Infrastructure
```bash
# From repo root (where docker-compose.yml lives)
docker compose up -d
```
Starts Postgres (5433), Redis (6379), Kafka (9092).

### 2. Start Backend
```bash
cd ridehailing
./gradlew bootRun
```
App starts on `http://localhost:8080`. Database is auto-seeded with 2 test riders and 5 test drivers.

**→ See [TESTING.md](./docs/TESTING.md) for the full step-by-step demo flow.**

---

## Project Structure

```
src/main/java/com/gocomet/ridehailing/
├── common/         # Kafka config, Redis config, WebSocket, exceptions, RideEvent
├── driver/         # Driver profiles, location ingestion, geo-index, Kafka producer/consumer
├── ride/           # Ride creation, matching engine, state machine, Kafka producer/consumer
├── trip/           # Trip lifecycle, Haversine fare calculation
├── payment/        # PSP stub, idempotency, payment status tracking
├── pricing/        # Surge multiplier per geo-cell
├── notification/   # WebSocket/STOMP push to riders and drivers
└── rider/          # Rider profiles
```
