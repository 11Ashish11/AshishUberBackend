# LLD — Dispatch/Matching Deep Dive

This document is a low-level design walkthrough of the core matching engine — the most critical component of the platform.

**Class:** `MatchingService` | `LocationService`
**Topics covered:** driver discovery, distributed locking, assignment flow, decline handling

---

## Problem Statement

When a rider creates a ride request, we must:
1. Find the nearest available driver of the correct vehicle type within 5km
2. Ensure no two rides are offered to the same driver simultaneously
3. Handle driver declines gracefully by trying the next candidate
4. Handle the case where no drivers are available

All of this must complete in **<1s p95**.

---

## Algorithm: Step-by-Step

```
findAndAssignDriver(rideId):
  1. Load ride from Postgres
  2. Guard: skip if ride is not in REQUESTED or MATCHING status
  3. Set ride status → MATCHING (saves to Postgres)
  4. Query Redis GEOSEARCH:
       GEOSEARCH driver:locations FROMLONLAT {pickup_lng} {pickup_lat}
         BYRADIUS 5 km ASC COUNT 10
         FILTER vehicle_type={vehicleTier}
     → Returns list of driverIds sorted by distance
  5. For each driverId in results:
     a. Skip if ride was already offered to this driver (check ride_assignments)
     b. Try to acquire Redis lock:
          SET driver:lock:{driverId} {rideId} NX PX 20000
        → NX = only set if not exists (atomic)
        → PX 20000 = auto-expire lock after 20s
     c. If lock acquired:
          - Load driver from Postgres
          - Verify driver.status == AVAILABLE (double-check)
          - Create RideAssignment record (status: OFFERED)
          - Set ride status → MATCHED
          - Notify driver via WebSocket (RIDE_OFFER)
          - Notify rider via WebSocket (DRIVER_MATCHED)
          - Return ✅
     d. If lock NOT acquired → driver is being assigned elsewhere → try next
  6. If all candidates exhausted:
     - Set ride status → NO_DRIVERS_AVAILABLE
     - Notify rider via WebSocket
```

---

## Redis Data Structures

### Geo Index
```
Key:   driver:locations  (type: GEO / sorted set)
Value: {driverId} → {longitude, latitude}

Write: GEOADD driver:locations {lng} {lat} {driverId}
Read:  GEOSEARCH driver:locations FROMLONLAT ... BYRADIUS 5 km ASC
```

### Availability Flag (TTL-based auto-expiry)
```
Key:   driver:available:{driverId}  (type: STRING)
Value: "1"
TTL:   30 seconds

Set:   SET driver:available:{driverId} 1 EX 30
       (refreshed every location update)
Auto-expire: if driver stops sending updates, removed in 30s
```

### Distributed Lock
```
Key:   driver:lock:{driverId}  (type: STRING)
Value: {rideId}  (so we know which ride holds the lock)
TTL:   20 seconds  (safety valve — auto-releases if app crashes)

Acquire: SET driver:lock:{driverId} {rideId} NX PX 20000
Release: DEL driver:lock:{driverId}
```

---

## Decline Flow

```
Driver → POST /v1/drivers/{id}/decline?rideId={rideId}
  → Load RideAssignment, set status → DECLINED
  → LocationService.unlockDriver() — DEL driver:lock:{driverId}
  → Ride status → MATCHING
  → findAndAssignDriver(rideId) — retry with next candidate
```

The `ride_assignments` table acts as a blacklist — already-offered drivers are skipped in subsequent attempts (`existsByRideIdAndDriverId` check in step 5a).

---

## Accept Flow

```
Driver → POST /v1/drivers/{id}/accept?rideId={rideId}
  → Load RideAssignment, verify status == OFFERED
  → Set assignment status → ACCEPTED
  → Set driver status → BUSY (Postgres)
  → Set ride status → ACCEPTED (Postgres)
  → Create Trip record (status: IN_PROGRESS)
  → Notify rider via WebSocket (RIDE_ACCEPTED)
```

---

## Race Condition Analysis

| Race Condition | Protection |
|----------------|------------|
| Two rides offered to same driver simultaneously | Redis SET NX — only one can acquire lock |
| Driver accepts ride already taken by another | Assignment status must be OFFERED at accept time |
| App crashes while driver is locked | Redis lock TTL (20s) — auto-releases |
| Rider creates two rides quickly | Idempotency key + `findActiveRideForRider` check |
| Stale driver in Redis geo pool | 30s TTL on `driver:available:{id}` key |

---

## Latency Budget

| Step | Typical | Worst Case |
|------|---------|------------|
| Redis GEOSEARCH (300k drivers, 5km) | ~2ms | ~10ms |
| Redis SET NX lock | ~1ms | ~3ms |
| Postgres: load driver | ~5ms | ~20ms |
| Postgres: save ride + assignment | ~10ms | ~30ms |
| WebSocket notify | ~1ms | ~5ms |
| **Total** | **~19ms** | **~68ms** |

Well within the 1s p95 target for a single-region deployment.

---

## What's Not Implemented

| Gap | Impact | Production Solution |
|-----|--------|---------------------|
| Timeout-based reassignment | Driver who doesn't respond holds a ride | Scheduled job: scan MATCHED rides older than 30s → reassign |
| Assignment attempt cap | Infinite retries in theory | `MAX_ASSIGNMENT_ATTEMPTS = 3` constant exists but isn't enforced as a retry limit across decline cycles |
| p95 measurement | No SLO enforcement | Micrometer metrics + Grafana/New Relic dashboard |
