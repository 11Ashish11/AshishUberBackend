# APIs & Events

## REST API Reference

Base URL: `http://localhost:8080`

---

### Riders

#### `GET /v1/riders`
List all riders (for demo/frontend).

**Response 200**
```json
[
  {
    "id": "3f4e8c82-a590-4328-9066-5233c914b34e",
    "name": "Ashish",
    "email": "ashish@test.com",
    "phone": "+919876543210",
    "createdAt": "2026-02-19T23:55:36.863668"
  }
]
```

---

### Drivers

#### `GET /v1/drivers`
List all drivers.

**Response 200**
```json
[
  {
    "id": "bb6524fd-28eb-4887-908a-b4e24b3d4b36",
    "name": "Raju",
    "vehicleType": "SEDAN",
    "status": "AVAILABLE",
    "currentLat": 12.9718,
    "currentLng": 77.5948
  }
]
```

#### `POST /v1/drivers/{id}/location`
Update driver GPS location. Publishes to `driver-locations` Kafka topic.

**Request**
```json
{ "latitude": 12.9718, "longitude": 77.5948 }
```

**Response 200** — same as driver object above.

#### `POST /v1/drivers/{id}/online`
Mark driver as AVAILABLE and enter matching pool.

#### `POST /v1/drivers/{id}/offline`
Mark driver as OFFLINE and remove from matching pool.

#### `POST /v1/drivers/{id}/accept?rideId={rideId}`
Accept a ride offer. Creates a trip.

#### `POST /v1/drivers/{id}/decline?rideId={rideId}`
Decline a ride offer. Triggers reassignment to next nearest driver.

#### `GET /v1/drivers/{id}/pending-offers`
Get current pending ride offers for a driver.

---

### Rides

#### `POST /v1/rides`
Create a ride request. Triggers matching synchronously.

**Request**
```json
{
  "riderId": "3f4e8c82-a590-4328-9066-5233c914b34e",
  "pickupLat": 12.9716,
  "pickupLng": 77.5946,
  "destinationLat": 12.9352,
  "destinationLng": 77.6245,
  "vehicleTier": "SEDAN",
  "paymentMethod": "CASH",
  "idempotencyKey": "ride-001"
}
```

**Enums**
- `vehicleTier`: `AUTO` | `SEDAN` | `SUV`
- `paymentMethod`: `CASH` | `UPI` | `CARD`

**Response 201**
```json
{
  "id": "e5388d73-ebc5-4050-a59c-ed9397fdcae9",
  "riderId": "3f4e8c82-a590-4328-9066-5233c914b34e",
  "pickupLat": 12.9716,
  "pickupLng": 77.5946,
  "destinationLat": 12.9352,
  "destinationLng": 77.6245,
  "vehicleTier": "SEDAN",
  "status": "MATCHED",
  "assignedDriverId": "bb6524fd-28eb-4887-908a-b4e24b3d4b36",
  "assignedDriverName": "Raju",
  "tripId": null,
  "surgeMultiplier": 1,
  "estimatedFare": 62.22,
  "createdAt": "2026-02-21T14:57:26.754014"
}
```

**Ride Status Values:** `REQUESTED` → `MATCHING` → `MATCHED` → `ACCEPTED` → `NO_DRIVERS_AVAILABLE` | `CANCELLED` | `EXPIRED`

**Error Responses**
- `409 Conflict` — Rider already has an active ride
- `400 Bad Request` — Validation failure (missing required fields)

#### `GET /v1/rides/{id}`
Get current ride status.

#### `POST /v1/rides/{id}/cancel`
Cancel a ride. Only allowed in `REQUESTED`, `MATCHING`, `MATCHED` statuses.

- `409 Conflict` — Cannot cancel ride in ACCEPTED or later status

---

### Trips

#### `POST /v1/trips/{id}/end`
End a trip and calculate fare.

**Request**
```json
{ "endLat": 12.9352, "endLng": 77.6245 }
```

**Response 200**
```json
{
  "id": "trip-uuid",
  "rideId": "ride-uuid",
  "driverId": "driver-uuid",
  "riderId": "rider-uuid",
  "status": "COMPLETED",
  "startTime": "2026-02-21T14:57:30",
  "endTime": "2026-02-21T15:12:45",
  "distanceKm": 5.19,
  "baseFare": 62.22,
  "surgeMultiplier": 1.00,
  "totalFare": 62.22
}
```

#### `GET /v1/trips/{id}`
Get trip details.

---

### Payments

#### `POST /v1/payments`
Trigger payment for a completed trip. PSP stub simulates Razorpay (90% success rate).

**Request**
```json
{
  "tripId": "trip-uuid",
  "paymentMethod": "CASH",
  "idempotencyKey": "pay-001"
}
```

**Response 200**
```json
{
  "id": "payment-uuid",
  "tripId": "trip-uuid",
  "amount": 62.22,
  "currency": "INR",
  "status": "SUCCESS",
  "paymentMethod": "CASH",
  "pspTransactionId": "PSP-xxxx"
}
```

**Payment Status Values:** `PENDING` → `PROCESSING` → `SUCCESS` | `FAILED`

---

### Config

#### `GET /v1/config/vehicle-tiers`
Returns: `["AUTO", "SEDAN", "SUV"]`

#### `GET /v1/config/payment-methods`
Returns: `["CASH", "UPI", "CARD"]`

---

## Kafka Events

### Topic: `ride-events`

**Key:** `rideId` (ensures all events for a ride land in the same partition → ordered)
**Partitions:** 2

**Schema: `RideEvent`**
```json
{
  "eventId": "uuid",
  "rideId": "uuid",
  "riderId": "uuid",
  "driverId": "uuid | null",
  "eventType": "REQUESTED | DRIVER_ASSIGNED | TRIP_STARTED | TRIP_COMPLETED | CANCELLED | NO_DRIVERS",
  "timestamp": "2026-02-21T14:57:26.621689Z",
  "metadata": { "key": "value" }
}
```

**Events Published**

| Trigger | `eventType` | `driverId` | `metadata` |
|---------|-------------|------------|------------|
| Ride created | `REQUESTED` | null | — |
| Driver accepts | `DRIVER_ASSIGNED` | driver's UUID | — |
| Trip started | `TRIP_STARTED` | driver's UUID | — |
| Trip completed | `TRIP_COMPLETED` | driver's UUID | fare, distance |
| Ride cancelled | `CANCELLED` | null | reason |
| No drivers found | `NO_DRIVERS` | null | — |

**Consumer Group:** `ride-state-tracker`

---

### Topic: `driver-locations`

**Key:** `driverId`
**Partitions:** 2

**Schema** (raw GPS payload published by `DriverLocationProducer`)
```json
{
  "driverId": "uuid",
  "latitude": 12.9718,
  "longitude": 77.5948,
  "status": "AVAILABLE",
  "vehicleType": "SEDAN",
  "timestamp": "..."
}
```

**Consumer Group:** `driver-location-tracker`

---

### Topic: `ride-requests`

**Key:** `requestId`
**Partitions:** 2

Command topic — published when a ride request is submitted. Designed for extension (e.g., separate matching microservice consuming this topic in a future architecture).

---

## WebSocket Events (STOMP)

Clients connect to `ws://localhost:8080/ws` and subscribe to topic channels.

| Channel | Subscriber | Event Types |
|---------|------------|-------------|
| `/topic/rider/{riderId}` | Rider | `DRIVER_MATCHED`, `TRIP_COMPLETED`, `PAYMENT_SUCCESS`, `PAYMENT_FAILED`, `NO_DRIVERS_AVAILABLE` |
| `/topic/driver/{driverId}` | Driver | `RIDE_OFFER` |
| `/topic/locations` | Frontend map | Driver GPS broadcast (lat, lng per driverId) |
