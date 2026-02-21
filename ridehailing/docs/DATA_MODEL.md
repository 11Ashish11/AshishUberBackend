# Data Model

ERD and schema for the **Dispatch/Matching** component (the core of the system).

---

## Entity Relationship Diagram

```
┌─────────────────┐        ┌─────────────────────────┐        ┌─────────────────┐
│     riders      │        │          rides           │        │     drivers     │
├─────────────────┤        ├─────────────────────────┤        ├─────────────────┤
│ id (PK, UUID)   │◄──────►│ id (PK, UUID)           │◄──────►│ id (PK, UUID)   │
│ name            │        │ rider_id (FK → riders)  │        │ name            │
│ email (unique)  │        │ assigned_driver_id (FK) │        │ email (unique)  │
│ phone           │        │ pickup_lat              │        │ phone           │
│ created_at      │        │ pickup_lng              │        │ vehicle_type    │
└─────────────────┘        │ destination_lat         │        │ status          │
                           │ destination_lng         │        │ current_lat     │
                           │ vehicle_tier            │        │ current_lng     │
                           │ status                  │        │ created_at      │
                           │ surge_multiplier        │        │ updated_at      │
                           │ estimated_fare          │        └─────────────────┘
                           │ idempotency_key (uniq)  │
                           │ created_at              │
                           │ updated_at              │        ┌──────────────────────┐
                           └────────────┬────────────┘        │   ride_assignments   │
                                        │                     ├──────────────────────┤
                                        └────────────────────►│ id (PK, UUID)        │
                                                              │ ride_id (FK → rides) │
                                        ┌────────────────────►│ driver_id (FK)       │
                                        │                     │ status               │
                           ┌────────────┴────────────┐        │ offered_at           │
                           │          trips           │        │ responded_at         │
                           ├─────────────────────────┤        └──────────────────────┘
                           │ id (PK, UUID)            │
                           │ ride_id (FK → rides)    │        ┌─────────────────────┐
                           │ driver_id (FK → drivers)│        │      payments        │
                           │ rider_id (FK → riders)  │        ├─────────────────────┤
                           │ status                  │◄──────►│ id (PK, UUID)        │
                           │ start_time              │        │ trip_id (FK → trips) │
                           │ end_time                │        │ rider_id (FK)        │
                           │ start_lat / start_lng   │        │ amount               │
                           │ end_lat / end_lng       │        │ currency             │
                           │ distance_km             │        │ status               │
                           │ base_fare               │        │ payment_method       │
                           │ total_fare              │        │ psp_transaction_id   │
                           │ surge_multiplier        │        │ idempotency_key      │
                           └─────────────────────────┘        │ created_at           │
                                                              └─────────────────────┘
```

---

## Table Schemas

### `riders`
```sql
CREATE TABLE riders (
  id          UUID PRIMARY KEY,
  name        VARCHAR NOT NULL,
  email       VARCHAR UNIQUE NOT NULL,
  phone       VARCHAR,
  created_at  TIMESTAMP NOT NULL
);
```

### `drivers`
```sql
CREATE TABLE drivers (
  id           UUID PRIMARY KEY,
  name         VARCHAR NOT NULL,
  email        VARCHAR UNIQUE,
  phone        VARCHAR,
  vehicle_type VARCHAR NOT NULL CHECK (vehicle_type IN ('AUTO','SEDAN','SUV')),
  status       VARCHAR NOT NULL CHECK (status IN ('AVAILABLE','BUSY','OFFLINE')),
  current_lat  DOUBLE PRECISION,
  current_lng  DOUBLE PRECISION,
  created_at   TIMESTAMP NOT NULL,
  updated_at   TIMESTAMP
);
```

### `rides`
```sql
CREATE TABLE rides (
  id                  UUID PRIMARY KEY,
  rider_id            UUID NOT NULL REFERENCES riders(id),
  assigned_driver_id  UUID REFERENCES drivers(id),
  pickup_lat          DOUBLE PRECISION NOT NULL,
  pickup_lng          DOUBLE PRECISION NOT NULL,
  destination_lat     DOUBLE PRECISION NOT NULL,
  destination_lng     DOUBLE PRECISION NOT NULL,
  vehicle_tier        VARCHAR NOT NULL CHECK (vehicle_tier IN ('AUTO','SEDAN','SUV')),
  status              VARCHAR NOT NULL CHECK (status IN (
                        'REQUESTED','MATCHING','MATCHED','ACCEPTED',
                        'CANCELLED','EXPIRED','NO_DRIVERS_AVAILABLE')),
  surge_multiplier    NUMERIC(4,2),
  estimated_fare      NUMERIC(10,2),
  idempotency_key     VARCHAR UNIQUE,
  created_at          TIMESTAMP NOT NULL,
  updated_at          TIMESTAMP
);

CREATE INDEX idx_rides_rider_id     ON rides(rider_id);
CREATE INDEX idx_rides_status       ON rides(status);
CREATE INDEX idx_rides_rider_status ON rides(rider_id, status);  -- active ride check
```

### `ride_assignments`
```sql
CREATE TABLE ride_assignments (
  id           UUID PRIMARY KEY,
  ride_id      UUID NOT NULL REFERENCES rides(id),
  driver_id    UUID NOT NULL REFERENCES drivers(id),
  status       VARCHAR NOT NULL CHECK (status IN ('OFFERED','ACCEPTED','DECLINED')),
  offered_at   TIMESTAMP NOT NULL,
  responded_at TIMESTAMP
);
```

> **Why this table?** Tracks the full chain of driver offers per ride. Enables skip-already-offered logic, debugging, and fairness analysis.

### `trips`
```sql
CREATE TABLE trips (
  id               UUID PRIMARY KEY,
  ride_id          UUID UNIQUE NOT NULL REFERENCES rides(id),
  driver_id        UUID NOT NULL REFERENCES drivers(id),
  rider_id         UUID NOT NULL REFERENCES riders(id),
  status           VARCHAR NOT NULL CHECK (status IN ('IN_PROGRESS','COMPLETED')),
  start_time       TIMESTAMP,
  end_time         TIMESTAMP,
  start_lat        DOUBLE PRECISION,
  start_lng        DOUBLE PRECISION,
  end_lat          DOUBLE PRECISION,
  end_lng          DOUBLE PRECISION,
  distance_km      NUMERIC(10,3),
  base_fare        NUMERIC(10,2),
  surge_multiplier NUMERIC(4,2),
  total_fare       NUMERIC(10,2)
);
```

### `payments`
```sql
CREATE TABLE payments (
  id                UUID PRIMARY KEY,
  trip_id           UUID NOT NULL REFERENCES trips(id),
  rider_id          UUID NOT NULL REFERENCES riders(id),
  amount            NUMERIC(10,2) NOT NULL,
  currency          VARCHAR NOT NULL DEFAULT 'INR',
  status            VARCHAR NOT NULL CHECK (status IN ('PENDING','PROCESSING','SUCCESS','FAILED')),
  payment_method    VARCHAR NOT NULL CHECK (payment_method IN ('CASH','UPI','CARD')),
  psp_transaction_id VARCHAR,
  idempotency_key   VARCHAR UNIQUE NOT NULL,
  created_at        TIMESTAMP NOT NULL
);
```

---

## Key Design Decisions

| Decision | Reason |
|----------|--------|
| `rides` and `trips` are separate tables | A ride is a request; a trip is the journey. Not every ride becomes a trip (cancelled, no drivers). Separation keeps audit trail clean. |
| `ride_assignments` junction table | Allows multiple driver offers per ride (decline chain). Provides a full audit log of who was offered what. |
| `idempotency_key` UNIQUE on `rides` and `payments` | Prevents duplicate booking and duplicate charges from network retries without application-layer state. |
| `assigned_driver_id` on `rides` | Fast lookup of which driver has the ride — avoids joins in matching hot path. |
| Composite index `(rider_id, status)` on `rides` | Optimises the frequent "does this rider have an active ride?" check at ride creation time. |
