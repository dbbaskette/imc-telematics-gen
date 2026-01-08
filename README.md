# Insurance Megacorp Telematics Generator

A Spring Boot application that simulates realistic vehicle telematics data for insurance use cases. Generates high-volume telemetry events including crash detection with multiple accident types.

## Features

- **Multi-Driver Simulation**: 15+ drivers with individual behaviors, vehicles, and daily routes
- **Circular Daily Routes**: Each driver follows a realistic daily loop (home → work → errands → home)
- **Real Atlanta Roads**: Routes use actual GPS coordinates with real speed limits from route data
- **9 Accident Types**: Rear-end, T-bone, head-on, rollover, side-swipe, hit-and-run, and more
- **Realistic Crash Sensors**: Each accident type has characteristic accelerometer/gyroscope patterns
- **Speed at Impact**: Captures actual driver speed when crash occurs
- **Real-Time Dashboard**: Live map visualization with crash triggering controls
- **High-Volume Output**: 300+ messages/second to RabbitMQ

## Tech Stack

- Java 21
- Spring Boot 3.5.3
- Spring AMQP (RabbitMQ)
- Spring WebSocket
- Leaflet.js (mapping)

## Quick Start

```bash
# Start with all dependencies (RabbitMQ auto-managed)
./imc-telematics-gen.sh --start

# Build and start
./imc-telematics-gen.sh --build --start

# Stop
./imc-telematics-gen.sh --stop
```

**Dashboard**: http://localhost:8082

## Accident Types

Each accident type generates unique sensor signatures:

| Type | Description | Sensor Pattern |
|------|-------------|----------------|
| REAR_ENDED | Struck from behind | Strong forward jolt (+X accel) |
| REAR_END_COLLISION | Struck another from behind | Strong deceleration (-X accel) |
| T_BONE | Side impact, perpendicular | High lateral force (+Y accel), roll |
| SIDE_SWIPE | Glancing side contact | Moderate lateral, yaw deviation |
| HEAD_ON | Frontal collision | Extreme deceleration, strong pitch |
| ROLLOVER | Vehicle rolled over | Chaotic all axes, high Z variation |
| SINGLE_VEHICLE | Hit fixed object | Strong frontal impact |
| MULTI_VEHICLE_PILEUP | Chain reaction | Multiple impact signatures |
| HIT_AND_RUN | Struck by fleeing vehicle | Variable based on impact angle |

## Message Format

Flat JSON structure optimized for performance:

```json
{
  "driver_id": 400018,
  "policy_id": 200018,
  "vehicle_id": 300021,
  "vin": "1HGBH41JXMN109186",
  "event_time": "2024-01-15T10:30:45.123Z",
  "speed_mph": 32.5,
  "speed_limit_mph": 35,
  "current_street": "Peachtree Street",
  "g_force": 1.18,
  "accident_type": null,
  "gps_latitude": 33.7701,
  "gps_longitude": -84.3876,
  "accelerometer_x": 0.12,
  "accelerometer_y": -0.05,
  "accelerometer_z": 0.98,
  "gyroscope_x": 0.02,
  "gyroscope_y": -0.01,
  "gyroscope_z": 0.15
}
```

**Crash events** include `accident_type` (e.g., "ROLLOVER") and elevated g-force/sensor values.

## Dashboard Controls

- **Trigger Accident**: Manually trigger crash for selected driver
- **Start All Driving**: Get all parked drivers moving
- **Pause/Resume**: Control simulation without stopping app
- **Message Rate**: Adjust telemetry frequency (200-2000ms)

## Configuration

Key settings in `application.yml`:

```yaml
telematics:
  simulation:
    interval-ms: 50              # Message frequency
    max-drivers: 15              # Number of active drivers
  behavior:
    post-crash-idle-minutes: 10  # Time driver sits after crash
    random-stop-probability: 0.05
```

## Driver Routes

Drivers follow circular daily routes stored in `src/main/resources/routes/daily/`. Each route includes:
- Real GPS coordinates
- Actual speed limits per road segment
- Street names
- Traffic control points

When a driver completes their route, they restart from the beginning (circular pattern).

## Docker

```bash
docker compose build
docker compose up -d
```

## Testing

```bash
mvn test
```
