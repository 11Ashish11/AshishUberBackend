# Resilience Plan

---

## What's Implemented

### 1. Idempotency (Duplicate Protection)

| API | Mechanism |
|-----|-----------|
| `POST /v1/rides` | `idempotencyKey` field → `UNIQUE` constraint in DB; duplicate request returns original response |
| `POST /v1/payments` | `idempotencyKey` field → `UNIQUE` constraint; duplicate payment returns existing record |
| Driver accept/decline | Assignment status checked before mutation — can't accept an already-ACCEPTED assignment |

**Why:** Mobile clients on flaky networks retry automatically. Without idempotency, a single tap can create multiple rides or double-charge a customer.

---

### 2. Distributed Locking (Double-Assignment Prevention)

```
Redis SET driver:lock:{driverId} {rideId} NX PX 20000
```

- `NX` — only set if key doesn't exist (atomic, no race condition)
- `PX 20000` — auto-expire after 20s if app crashes before releasing
- Released explicitly on: driver accept, driver decline, ride cancel

**Failure mode covered:** Two matching requests happening simultaneously for the same driver — only one acquires the lock.

---

### 3. TTL-Based Stale Data Cleanup

| Key | TTL | Purpose |
|-----|-----|---------|
| `driver:available:{id}` | 30s | Driver auto-removed from pool if they stop sending location |
| `driver:lock:{id}` | 20s | Lock auto-released if app crashes mid-assignment |
| `surge:{geohash}` | 60s | Surge resets if no new demand in the area |
| `demand:{geohash}` | 5min | Demand counter resets to avoid stale surge |

---

### 4. Kafka Error Handling

- **`ErrorHandlingDeserializer`** wraps `JsonDeserializer` — malformed messages are caught and logged rather than crashing the consumer thread
- **Type headers** on every message (`__TypeId__`) — correct class is always known at deserialization time
- **`auto-offset-reset=earliest`** — consumers replay from beginning on restart (guarantees no event loss)
- **`acks=all`** on producer — message only acknowledged when all in-sync replicas have written it

---

### 5. Active Ride Guard

Before creating a new ride, `RideService` checks:
```java
rideRepository.findActiveRideByRiderId(riderId)
```
Returns `409 Conflict` if an active ride already exists. Prevents the double-booking scenario.

---

### 6. State Machine Guards

Every state transition is validated before execution:

| Service | Guard |
|---------|-------|
| `RideService.cancelRide()` | Only cancels REQUESTED, MATCHING, or MATCHED — throws 409 otherwise |
| `TripService.endTrip()` | Only ends IN_PROGRESS trips — throws 400 otherwise |
| `RideService.acceptRide()` | Only accepts if assignment status is OFFERED |
| `MatchingService` | Only matches if ride is in REQUESTED or MATCHING |

---

## What's Not Implemented (Production Gaps)

### Missing: Circuit Breakers
**Gap:** If Postgres is slow or Redis is unavailable, requests pile up and cascade.
**Production fix:** Resilience4j `@CircuitBreaker` on service calls — fail fast and return degraded response when error rate exceeds threshold.

### Missing: Timeout-Based Reassignment
**Gap:** If a driver receives a ride offer but never responds, the ride stays in MATCHED indefinitely.
**Production fix:** Spring `@Scheduled` job — every 30s, query for MATCHED rides older than 60s, unlock the driver, retry matching.

### Missing: PSP Retry Logic
**Gap:** If payment fails (10% chance with stub), no automatic retry occurs.
**Production fix:** Exponential backoff retry with Resilience4j `@Retry` — 3 attempts with 1s, 2s, 4s delays; dead-letter queue for permanently failed payments.

### Missing: Consumer Backpressure
**Gap:** If the app can't process Kafka messages fast enough, the lag grows unbounded.
**Production fix:** Set `max.poll.records` and `max.poll.interval.ms`; add consumer group lag alerting; scale consumer replicas horizontally.

### Missing: PCI/PII Compliance
**Gap:** Payment data (amounts, PSP transaction IDs) stored in plaintext.
**Production fix:** Encrypt `payment.amount` and `psp_transaction_id` at rest (AES-256); mask rider phone/email in logs; GDPR right-to-erasure endpoint for rider data.

### Missing: Reconciliation Job
**Gap:** No process to detect payments stuck in PROCESSING or to reconcile with PSP.
**Production fix:** Nightly reconciliation job queries PSP API for transaction status; marks stuck payments as FAILED and alerts ops.

---

## Failure Mode Analysis

| Failure | Impact | Current Behaviour | Production Fix |
|---------|--------|-------------------|----------------|
| Redis down | Matching fails (can't geo-search or lock) | 500 error on ride creation | Circuit breaker + fallback to DB-based matching (degraded) |
| Kafka broker down | Events not published, consumers stop | App continues, events lost | Kafka is durable — consumers catch up on reconnect; monitor lag |
| Postgres down | Can't create/read rides | 500 error | Read replicas for reads; retry with backoff on writes |
| App crash during matching | Driver lock never released | Lock auto-expires in 20s | Already handled via TTL |
| Mobile client retries ride creation | Duplicate ride created | Idempotency key returns original | Already handled |
| PSP timeout | Payment hangs | No timeout — hangs forever | `@TimeLimiter` wrapping PSP call; treat timeout as FAILED |
