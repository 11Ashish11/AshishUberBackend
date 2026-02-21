# End-to-End Testing Guide

This guide walks a reviewer through setting up and running the complete ride-hailing system locally — from Docker installation to a full ride lifecycle.

---

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java | 17+ | [adoptium.net](https://adoptium.net) |
| Docker Desktop | Latest | [docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop) |

---

## Step 1 — Clone and Navigate

```bash
git clone <repo-url>
cd ride-hailing
```

---

## Step 2 — Start Infrastructure (Postgres + Redis + Kafka)

```bash
# From the repo root (where docker-compose.yml lives)
docker compose up -d
```

Verify all 3 containers are running:

```bash
docker ps
```

Expected output — all 3 should show `Up`:
```
ridehailing-kafka    Up    0.0.0.0:9092->9092/tcp
ridehailing-redis    Up    0.0.0.0:6379->6379/tcp
ridehailing-db       Up    0.0.0.0:5433->5432/tcp
```

---

## Step 3 — Start the Backend

```bash
cd ridehailing
./gradlew bootRun
```

Wait until you see in the logs:
```
Started RidehailingApplication in X.XXX seconds
```

> **First run:** The app automatically seeds the database with **2 riders** and **5 drivers** in Bangalore. No manual setup needed.

---

## Step 4 — Verify Demo Data

```bash
# List all riders
curl http://localhost:8080/v1/riders
```

You will see:
```json
[
  {"id": "3f4e8c82-a590-4328-9066-5233c914b34e", "name": "Ashish", "email": "ashish@test.com"},
  {"id": "dd5bf85e-71b5-4ec9-a551-468094b86bf0", "name": "Priya",  "email": "priya@test.com"}
]
```

```bash
# List all drivers
curl http://localhost:8080/v1/drivers
```

You will see 5 drivers — Raju, Kumar, Suresh (SEDAN/AUTO/SUV), Venkat, Anil.

---

## Step 5 — Full Ride Lifecycle Test

Run the following commands **in order**. Copy IDs from each response into the next command.

---

### 5a — Put a SEDAN driver online and send their location

This registers Raju in the Redis geo-index so the matching engine can find him.

```bash
# Go online
curl -X POST http://localhost:8080/v1/drivers/bb6524fd-28eb-4887-908a-b4e24b3d4b36/online

# Send location (near the pickup point below)
curl -X POST http://localhost:8080/v1/drivers/bb6524fd-28eb-4887-908a-b4e24b3d4b36/location \
  -H "Content-Type: application/json" \
  -d '{"latitude": 12.9718, "longitude": 77.5948}'
```

Expected: `"status": "AVAILABLE"` with updated coordinates.

---

### 5b — Rider creates a ride

```bash
curl -X POST http://localhost:8080/v1/rides \
  -H "Content-Type: application/json" \
  -d '{
    "riderId": "3f4e8c82-a590-4328-9066-5233c914b34e",
    "pickupLat": 12.9716,
    "pickupLng": 77.5946,
    "destinationLat": 12.9352,
    "destinationLng": 77.6245,
    "vehicleTier": "SEDAN",
    "paymentMethod": "CASH"
  }'
```

Expected response:
```json
{
  "id": "<RIDE_ID>",
  "status": "MATCHED",
  "assignedDriverId": "bb6524fd-28eb-4887-908a-b4e24b3d4b36",
  "estimatedFare": 62.22,
  ...
}
```

> **`MATCHED` means Kafka worked end-to-end:** the ride request was published to `ride-requests`, the matching engine ran, and the result was published to `ride-events`.

**Copy the `id` field — this is your `RIDE_ID`.**

---

### 5c — Driver accepts the ride

Replace `<RIDE_ID>` with the ID from step 5b:

```bash
curl -X POST "http://localhost:8080/v1/drivers/bb6524fd-28eb-4887-908a-b4e24b3d4b36/accept?rideId=<RIDE_ID>"
```

Expected: ride status changes to `ACCEPTED`, a trip is created. **Copy the `tripId` field.**

---

### 5d — End the trip

Replace `<TRIP_ID>` with the `tripId` from step 5c:

```bash
curl -X POST http://localhost:8080/v1/trips/<TRIP_ID>/end \
  -H "Content-Type: application/json" \
  -d '{"endLat": 12.9352, "endLng": 77.6245}'
```

Expected: `"status": "COMPLETED"` with final fare.

---

### 5e — Process payment

```bash
curl -X POST http://localhost:8080/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "tripId": "<TRIP_ID>",
    "paymentMethod": "CASH",
    "idempotencyKey": "pay-demo-001"
  }'
```

Expected: `"status": "SUCCESS"` (PSP stub simulates Razorpay — 90% success rate).

---

## Step 6 — Verify Kafka Topics (Optional)

In a separate terminal, watch events flow in real-time:

```bash
# Watch all ride state changes
docker exec ridehailing-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic ride-events \
  --from-beginning

# Watch driver GPS pings
docker exec ridehailing-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic driver-locations \
  --from-beginning
```

List all topics:
```bash
docker exec ridehailing-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

---

## Troubleshooting

### ⚠️ `409 Conflict — Rider already has an active ride` (most common)

This happens when a previous test run left a ride unfinished. **One command fixes it:**

```bash
# Cancel ALL stale active rides for Ashish (safe to run anytime)
docker exec ridehailing-db psql -U admin -d ridehailing \
  -c "UPDATE rides SET status = 'CANCELLED' WHERE rider_id = '3f4e8c82-a590-4328-9066-5233c914b34e' AND status NOT IN ('CANCELLED','COMPLETED','NO_DRIVERS_AVAILABLE','EXPIRED');"
```

Then go back to **Step 5a** and start fresh.

> **Why can't I use the cancel API?** `POST /v1/rides/{id}/cancel` only works for rides in `REQUESTED`, `MATCHING`, or `MATCHED` status. If a previous test left the ride in `ACCEPTED` status (driver accepted it), the API rejects it — the DB update above bypasses this.

---

### `NO_DRIVERS_AVAILABLE`

The driver's location was not registered in Redis (it resets on Docker restart). Run **Step 5a first** — always send the driver's location before creating a ride.

---

### Kafka consumer errors on startup

Stale messages from a previous Docker session. Delete and recreate the problem topic:
```bash
docker exec ridehailing-kafka kafka-topics --bootstrap-server localhost:9092 --delete --topic ride-events
# Kafka will auto-recreate it on next message
```


---

## Valid Enum Values

| Field | Values |
|-------|--------|
| `vehicleTier` | `AUTO`, `SEDAN`, `SUV` |
| `paymentMethod` | `CASH`, `UPI`, `CARD` |

---

## Seeded Demo IDs (for copy-paste)

| Entity | Name | ID |
|--------|------|----|
| Rider | Ashish | `3f4e8c82-a590-4328-9066-5233c914b34e` |
| Rider | Priya | `dd5bf85e-71b5-4ec9-a551-468094b86bf0` |
| Driver | Raju (SEDAN) | `bb6524fd-28eb-4887-908a-b4e24b3d4b36` |
| Driver | Kumar (AUTO) | `5c799d85-d613-4bf1-a54f-78e4e4ff90e4` |
| Driver | Suresh (SUV) | `a08cd1bf-495b-4316-8f8d-31de65eae73c` |
| Driver | Venkat (SEDAN) | `4bdf9826-43f2-4504-af63-7d03fdd55d3f` |
| Driver | Anil (AUTO) | `13375c88-1211-4a68-9f13-8c723b1ef2cd` |
