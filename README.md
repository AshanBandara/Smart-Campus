# Smart Campus — Sensor & Room Management API

A RESTful API built with **JAX-RS (Jersey 2.41)** and an embedded **Grizzly HTTP server** for "Smart Campus" initiative. The system manages campus Rooms and IoT Sensors (temperature, CO2, occupancy, etc.) via a fully versioned REST interface.

---

## Table of Contents

1. [API Design Overview](#api-design-overview)
2. [Technology Stack](#technology-stack)
3. [Project Structure](#project-structure)
4. [Build & Run Instructions](#build--run-instructions)
5. [API Endpoints Reference](#api-endpoints-reference)
6. [Sample curl Commands](#sample-curl-commands)

---

## API Design Overview

The API follows RESTful principles with a clear resource hierarchy that mirrors the physical structure of the campus. All resources are served under the `/api/v1/` base path:

```
GET  /api/v1/discovery                       ← API metadata + HATEOAS resource links
GET  /api/v1/rooms                           ← List all rooms
POST /api/v1/rooms                           ← Create a room
GET  /api/v1/rooms/{roomId}                  ← Get a specific room
DEL  /api/v1/rooms/{roomId}                  ← Delete a room (blocked if sensors exist)
GET  /api/v1/sensors                         ← List all sensors (filter: ?type=)
POST /api/v1/sensors                         ← Register a sensor (validates roomId)
GET  /api/v1/sensors/{sensorId}              ← Get a specific sensor
GET  /api/v1/sensors/{sensorId}/readings     ← Get reading history
POST /api/v1/sensors/{sensorId}/readings     ← Add a reading (updates currentValue)
```

**Key design decisions:**

- **In-memory storage** — all data lives in `static ConcurrentHashMap` fields inside `DataStore.java`. `ConcurrentHashMap` is used instead of plain `HashMap` to prevent race conditions when multiple requests arrive simultaneously.
- **Explicit class registration** — all resources, filters, and exception mappers are registered manually in `Main.java` via `ResourceConfig` (not via `SmartCampusApplication`) for reliable Jersey discovery without classpath scanning issues. `SmartCampusApplication` exists solely to declare the `@ApplicationPath("/api/v1")` entry point.
- **Sub-resource locator pattern** — `SensorResource` delegates `/sensors/{id}/readings` to a dedicated `SensorReadingResource` class, keeping each class focused and independently testable.
- **Leak-proof error handling** — four `ExceptionMapper` implementations ensure no raw Java stack traces are ever returned to clients. All errors return structured JSON.
- **Cross-cutting logging** — a single `LoggingFilter` class implements both `ContainerRequestFilter` and `ContainerResponseFilter`, logging every request and response without touching any resource method.

---

## Technology Stack

| Component      | Technology                               |
|----------------|------------------------------------------|
| API Framework  | JAX-RS 2.1 (Jersey 2.41)                |
| HTTP Server    | Grizzly 2 (embedded, no external server) |
| JSON           | Jackson (jersey-media-json-jackson)      |
| Build Tool     | Apache Maven                             |
| Java Version   | Java 11 (Eclipse Temurin)                |
| Data Storage   | In-memory `ConcurrentHashMap`            |



---

## Project Structure

```
Smart-Campus/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── com/smartcampus/
                ├── app/
                │   ├── Main.java                          # Starts Grizzly server on port 8080
                │   └── SmartCampusApplication.java        # @ApplicationPath("/api/v1")
                ├── model/
                │   ├── Room.java                          # id, name, capacity, sensorIds
                │   ├── Sensor.java                        # id, type, status, currentValue, roomId
                │   └── SensorReading.java                 # id (UUID), timestamp, value
                ├── store/
                │   └── DataStore.java                     # Static ConcurrentHashMaps + seed data
                ├── resource/
                │   ├── DiscoveryResource.java             # GET /api/v1/discovery
                │   ├── RoomResource.java                  # GET/POST/DELETE /rooms
                │   ├── SensorResource.java                # GET/POST /sensors + sub-resource locator
                │   └── SensorReadingResource.java         # GET/POST readings (sub-resource)
                ├── exception/
                │   ├── RoomNotEmptyException.java
                │   ├── RoomNotEmptyExceptionMapper.java        # → 409 Conflict
                │   ├── LinkedResourceNotFoundException.java
                │   ├── LinkedResourceNotFoundExceptionMapper.java  # → 422 Unprocessable Entity
                │   ├── SensorUnavailableException.java
                │   ├── SensorUnavailableExceptionMapper.java  # → 403 Forbidden
                │   └── GlobalExceptionMapper.java             # → 500 catch-all (Throwable)
                └── filter/
                    └── LoggingFilter.java                 # Logs all requests and responses
```

---

## Build & Run Instructions

### Prerequisites

- **Java 11** (Eclipse Temurin recommended — [download here](https://adoptium.net/))
- **Apache Maven 3.6+**
- **IntelliJ IDEA** (Community or Ultimate)

Verify your setup:
```bash
java -version   # should show 11.x
mvn -version    # should show 3.6+
```

### Step 1 — Clone the repository

```bash
git clone https://github.com/AshanBandara/Smart-Campus.git
cd Smart-Campus
```

### Step 2 — Open in IntelliJ IDEA

1. Open **IntelliJ IDEA**
2. Click **File → Open** → select the cloned `Smart-Campus` folder
3. IntelliJ detects `pom.xml` automatically → click **Load Maven Project** on the blue bar
4. Wait for all dependencies to download (bottom progress bar)
5. Confirm no red import errors in any `.java` file

### Step 3 — Configure the Run Configuration

1. Click the dropdown at the top right → **Edit Configurations...**
2. Click **+** → **Application**
3. Fill in:
    - **Name:** `Smart Campus API`
    - **Main class:** `com.smartcampus.app.Main`
    - **JDK:** Java 11
4. Click **OK**

### Step 4 — Run the server

Click the green **▶ Run** button or press **Shift+F10**.

You should see in the console:
```
INFO: Smart Campus API started at http://localhost:8080/api/v1/
INFO: Press ENTER to stop the server...
```

### Step 5 — Verify it is working

Open your browser and visit:

```
http://localhost:8080/api/v1/rooms
http://localhost:8080/api/v1/sensors
http://localhost:8080/api/v1/discovery
```

All three should return JSON responses immediately.

### Step 6 — Stop the server

Click the red **⬛ Stop** button in the Run panel, or press **Enter** in the console window.

---

## API Endpoints Reference

### Discovery

| Method | Path                  | Description                                              | Status |
|--------|-----------------------|----------------------------------------------------------|--------|
| GET    | /api/v1/discovery     | Returns API version, contact info, and HATEOAS links     | 200    |

### Rooms

| Method | Path                      | Description                                            | Success Status |
|--------|---------------------------|--------------------------------------------------------|----------------|
| GET    | /api/v1/rooms             | Returns list of all rooms with their sensor IDs        | 200            |
| POST   | /api/v1/rooms             | Creates a new room                                     | 201            |
| GET    | /api/v1/rooms/{roomId}    | Returns a single room's full details                   | 200            |
| DELETE | /api/v1/rooms/{roomId}    | Deletes a room — blocked if sensors are still assigned | 204            |

### Sensors

| Method | Path                       | Description                                              | Success Status |
|--------|----------------------------|----------------------------------------------------------|----------------|
| GET    | /api/v1/sensors            | Returns all sensors (add `?type=CO2` to filter)          | 200            |
| POST   | /api/v1/sensors            | Registers a new sensor (validates that roomId exists)    | 201            |
| GET    | /api/v1/sensors/{id}       | Returns a single sensor's full details                   | 200            |

### Sensor Readings (Sub-Resource)

| Method | Path                              | Description                                               | Success Status |
|--------|-----------------------------------|-----------------------------------------------------------|----------------|
| GET    | /api/v1/sensors/{id}/readings     | Returns full reading history for a sensor                 | 200            |
| POST   | /api/v1/sensors/{id}/readings     | Appends a new reading and updates sensor's `currentValue` | 201            |

### Error Responses Reference

| HTTP Status | When it occurs                                               |
|-------------|--------------------------------------------------------------|
| 400         | Missing required fields (e.g. no room ID in POST body)      |
| 403         | POST reading attempted on a `MAINTENANCE` sensor             |
| 404         | Room or sensor ID not found in the system                    |
| 409         | DELETE attempted on a room that still has sensors assigned   |
| 415         | Request sent with wrong Content-Type (must be application/json) |
| 422         | Sensor POST references a `roomId` that does not exist        |
| 500         | Any unexpected server error — clean JSON, no stack trace     |

---

## Sample curl Commands

> Server runs on `http://localhost:8080`. All POST requests require the header `Content-Type: application/json`.

### 1. Discover the API (HATEOAS)

```bash
curl -s http://localhost:8080/api/v1/discovery
```

Expected response `200 OK`:
```json
{
  "api": "Smart Campus Sensor & Room Management API",
  "version": "1.0",
  "contact": "w2153374@westminster.ac.uk",
  "description": "RESTful API for managing campus rooms and IoT sensors.",
  "resources": {
    "rooms": "/api/v1/rooms",
    "sensors": "/api/v1/sensors"
  }
}
```

---

### 2. List all rooms

```bash
curl -s http://localhost:8080/api/v1/rooms
```

Expected response `200 OK`:
```json
[
  {"id":"LIB-301","name":"Library Quiet Study","capacity":50,"sensorIds":["TEMP-001","CO2-001"]},
  {"id":"LAB-101","name":"Computer Lab","capacity":30,"sensorIds":["OCC-001"]}
]
```

---

### 3. Create a new room

```bash
curl -s -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"ENG-205","name":"Engineering Seminar Room","capacity":40}'
```

Expected response `201 Created`:
```json
{"id":"ENG-205","name":"Engineering Seminar Room","capacity":40,"sensorIds":[]}
```

---

### 4. Register a sensor linked to a room

```bash
curl -s -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"CO2-002","type":"CO2","status":"ACTIVE","currentValue":400.0,"roomId":"ENG-205"}'
```

Expected response `201 Created`:
```json
{"id":"CO2-002","type":"CO2","status":"ACTIVE","currentValue":400.0,"roomId":"ENG-205"}
```

---

### 5. Filter sensors by type

```bash
curl -s "http://localhost:8080/api/v1/sensors?type=CO2"
```

Returns only CO2 sensors. Filtering is case-insensitive — `?type=co2` and `?type=CO2` both work.

---

### 6. Post a new reading to a sensor

```bash
curl -s -X POST http://localhost:8080/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":23.5}'
```

Expected response `201 Created`:
```json
{"id":"a3f7e210-...","timestamp":1714000000000,"value":23.5}
```

After this call, `GET /api/v1/sensors/TEMP-001` will show `"currentValue": 23.5`.

---

### 7. Get reading history for a sensor

```bash
curl -s http://localhost:8080/api/v1/sensors/TEMP-001/readings
```

---

### 8. Attempt to delete a room that has sensors — 409 Conflict

```bash
curl -s -X DELETE http://localhost:8080/api/v1/rooms/LIB-301
```

Expected response `409 Conflict`:
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Room 'LIB-301' cannot be deleted: it still has 2 active sensor(s) assigned. Please reassign or remove all sensors before decommissioning this room.",
  "roomId": "LIB-301",
  "sensorCount": 2
}
```

---

### 9. Post a reading to a MAINTENANCE sensor — 403 Forbidden

```bash
curl -s -X POST http://localhost:8080/api/v1/sensors/OCC-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":15}'
```

Expected response `403 Forbidden`:
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "Sensor 'OCC-001' is currently under MAINTENANCE and cannot accept new readings. Update the sensor status to ACTIVE first.",
  "sensorId": "OCC-001"
}
```

---

### 10. Register a sensor with a non-existent roomId — 422 Unprocessable Entity

```bash
curl -s -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-999","type":"Temperature","status":"ACTIVE","currentValue":0.0,"roomId":"FAKE-ROOM"}'
```

Expected response `422 Unprocessable Entity`:
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "The field 'roomId' references a resource that does not exist: 'FAKE-ROOM'. Ensure the referenced resource is created first.",
  "field": "roomId",
  "invalidValue": "FAKE-ROOM"
}
```

---
