<div align="center">

<img src="docs/logo.svg" alt="IMC Telematics" width="720"/>

<h1>Insurance Megacorp Telematics Generator</h1>

<p>
  🚗 Realistic multi-driver telematics simulator • 🌐 Live map dashboard • 🐇 RabbitMQ JSON events
</p>

</div>

A Spring Boot application that simulates a vehicle's telematics device, sending continuous sensor data to RabbitMQ with periodic crash events.

## Features

- **Multi-Driver Simulation**: Simulates multiple drivers with individual behaviors and states
- **Realistic Driver States**: DRIVING, PARKED, POST_CRASH_IDLE, TRAFFIC_STOP, BREAK_TIME
- **Real Road Networks**: Drivers follow actual Atlanta street routes with proper GPS coordinates
- **File-Based Route System**: Routes loaded from JSON files for realistic movement patterns
- **Route Generation Utility**: Generate new routes using OpenRouteService API
- **Post-Crash Behavior**: Drivers sit still for configurable periods after crash events  
- **Dynamic State Changes**: Drivers randomly stop, take breaks, encounter traffic, etc.
- **Individual Tracking**: Each driver has unique policy ID, location, and message history
- **Continuous Data Stream**: Sends realistic telemetry data every 1.5 seconds
- **High G-Force Simulation**: Realistic crash events generate high G-force sensor readings
- **Crash Simulation**: Periodic crash events with high G-force readings
- **Web Dashboard**: Real-time map visualization of all drivers and their states
- **Dual Configuration**: Supports both local development and Cloud Foundry deployment
- **JSON Messaging**: Sends structured JSON messages to RabbitMQ queues
- **Configurable Parameters**: Driver count, behavior probabilities, crash frequency, idle times

## Tech Stack

- Java 21
- Spring Boot 3.5.3
- Spring AMQP (RabbitMQ)
- Spring WebSocket (Real-time dashboard)
- Leaflet.js (Interactive mapping)
- Maven

## Prerequisites

- Java 21 or higher
- Maven 3.6+
- Docker (for local development with automatic RabbitMQ setup)

## Quick Start

### Using the Control Script (Recommended)

```bash
# Start the application with all dependencies
./imc-telematics-gen.sh --start

# Check application status
./imc-telematics-gen.sh --status

# Watch logs in real-time
./imc-telematics-gen.sh --logs

# Clean shutdown
./imc-telematics-gen.sh --stop
```

**Control Script Features:**
- Automatic RabbitMQ container management
- PID file tracking and health checks
- Graceful shutdown with proper cleanup
- Real-time log tailing
- Color-coded status output

Once running, access the **Web Dashboard** at: http://localhost:8082

### Manual Local Development

1. **Start RabbitMQ**:
   ```bash
   # Using Docker (same image as control script)
   docker run -d --name rabbitmq-telematics -p 5672:5672 -p 15672:15672 rabbitmq:3.13-management-alpine
   
   # Or using local installation
   rabbitmq-server
   ```

2. **Build and Run**:
   ```bash
   mvn clean compile
   mvn spring-boot:run
   ```

3. **Access Interfaces**:
   - **Web Dashboard**: http://localhost:8082 (real-time driver map)
   - **RabbitMQ Management**: http://localhost:15672 (guest/guest)
   - **Health Check**: http://localhost:8082/actuator/health
   - Check the `telematics_work_queue.crash-detection-group` queue for messages

### Cloud Foundry Deployment

```bash
# Set the cloud profile
export SPRING_PROFILES_ACTIVE=cloud

# Build the application
mvn clean package

# Deploy to Cloud Foundry
cf push telematicsgen -p target/telematicsgen-0.0.1-SNAPSHOT.jar
```

## Route Management

### Current Route Files

The application includes predefined Atlanta-area routes:
- **peachtree_south**: Peachtree Street from Midtown to Downtown
- **downtown_connector**: I-75/I-85 Downtown Connector 
- **cumming_to_airport**: Route from Cumming, GA to Hartsfield Airport

Routes are stored as JSON files in `src/main/resources/routes/`.

### Generating New Routes

Use the RouteGenerator utility to create new route files using the OpenRouteService API:

```bash
# Interactive route generator
./generate-routes.sh
```

**Prerequisites for Route Generation:**
1. Get a free API key from [OpenRouteService](https://openrouteservice.org/)
2. Set your API key:
   ```bash
   export OPENROUTE_API_KEY=your_api_key_here
   ```

**Route Generator Options:**
- **Generate custom route**: Enter any start/end addresses
- **Generate predefined routes**: Creates additional Atlanta-area routes
- **Automatic file creation**: Saves routes to `src/main/resources/routes/`

**Route File Format:**
```json
{
  "name": "route_name",
  "description": "Start Location → End Location",
  "start_location": "Start Address",
  "end_location": "End Address", 
  "waypoints": [
    {
      "latitude": 33.7701,
      "longitude": -84.3876,
      "street_name": "Peachtree St & 10th St",
      "speed_limit": 35,
      "has_traffic_light": true,
      "traffic_control": "traffic_light"
    }
  ]
}
```

## Configuration

### Local Configuration (`application.yml`)
- **RabbitMQ**: localhost:5672
- **Queue**: `telematics_work_queue.crash-detection-group`
- **Base Policy ID**: `IMC-AUTO-98765` (with sequential driver IDs)
- **Routes**: File-based loading from `src/main/resources/routes/`
- **Driver Count**: 3 drivers
- **Simulation Interval**: 500ms
- **Crash Frequency**: 50 messages between crash events per driver
- **Post-Crash Idle**: 10 minutes
- **Random Stop Probability**: 5%
- **Break Duration**: 5 minutes

### Cloud Configuration (`application-cloud.yml`)
Uses environment variables for Cloud Foundry deployment:
- `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`
- `TELEMATICS_QUEUE_NAME`, `POLICY_ID`
- `VEHICLE_LATITUDE`, `VEHICLE_LONGITUDE`
- `DRIVER_COUNT`, `CRASH_FREQUENCY`
- `POST_CRASH_IDLE_MINUTES`, `RANDOM_STOP_PROBABILITY`, `BREAK_DURATION_MINUTES`

## Message Format

The application sends enhanced telemetry data with comprehensive sensor information in JSON format to RabbitMQ.

### Enhanced Telemetry Message Structure
```json
{
  "policy_id": 200018,
  "vehicle_id": 300021,
  "vin": "1HGBH41JXMN109186",
  "timestamp": "2024-01-15T10:30:45.123Z",
  "speed_mph": 32.5,
  "current_street": "Peachtree Street",
  "g_force": 1.18,
  "sensors": {
    "gps": {
      "latitude": 33.7701,
      "longitude": -84.3876,
      "altitude": 351.59,
      "speed_ms": 14.5,
      "bearing": 148.37,
      "accuracy": 2.64,
      "satellite_count": 11,
      "gps_fix_time": 150
    },
    "accelerometer": {
      "x": 0.1234,
      "y": -0.0567,
      "z": 0.9876
    },
    "gyroscope": {
      "pitch": 0.02,
      "roll": -0.01,
      "yaw": 0.15
    },
    "magnetometer": {
      "x": 25.74,
      "y": -8.73,
      "z": 40.51,
      "heading": 148.37
    },
    "barometric_pressure": 1013.25,
    "device": {
      "battery_level": 82.0,
      "signal_strength": -63,
      "orientation": "portrait",
      "screen_on": false,
      "charging": true
    }
  }
}
```

### Crash Event Data (High G-Force)
```json
{
  "policy_id": 200034,
  "vehicle_id": 300038,
  "vin": "KNDJP3A57H7123456",
  "timestamp": "2024-01-15T10:30:45.123Z", 
  "speed_mph": 0.0,
  "current_street": "Highland Street",
  "g_force": 8.67,
  "sensors": {
    "gps": {
      "latitude": 33.7701,
      "longitude": -84.3876,
      "altitude": 345.12,
      "speed_ms": 0.0,
      "bearing": 148.37,
      "accuracy": 2.1,
      "satellite_count": 10,
      "gps_fix_time": 120
    },
    "accelerometer": {
      "x": 6.5432,
      "y": 5.8901,
      "z": 1.2345
    },
    "gyroscope": {
      "pitch": 2.45,
      "roll": -1.89,
      "yaw": 0.67
    },
    "magnetometer": {
      "x": 28.91,
      "y": -12.45,
      "z": 38.67,
      "heading": 145.23
    },
    "barometric_pressure": 1012.8,
    "device": {
      "battery_level": 78.0,
      "signal_strength": -58,
      "orientation": "landscape",
      "screen_on": true,
      "charging": false
    }
  }
}
```

### Enhanced Data Fields

**Core Message:**
- `policy_id`: Insurance policy identifier (e.g., "IMC-98675")
- `vin`: Vehicle Identification Number
- `current_street`: Real street name from GPS location
- `g_force`: Calculated G-force from accelerometer data

**Enhanced GPS Data:**
- `altitude`: Elevation in meters
- `speed_ms`: Speed in meters per second (in addition to mph)
- `bearing`: Direction of travel in degrees
- `accuracy`: GPS accuracy in meters
- `satellite_count`: Number of GPS satellites in view
- `gps_fix_time`: Time to acquire GPS fix in milliseconds

**Device Metadata:**
- `battery_level`: Device battery percentage
- `signal_strength`: Cellular signal strength in dBm
- `orientation`: Device orientation (portrait/landscape)
- `screen_on`: Whether device screen is active
- `charging`: Whether device is charging

**Environmental Data:**
- `barometric_pressure`: Atmospheric pressure in hPa
- `magnetometer.heading`: Magnetic compass heading

**Analysis Notes**: The telematics device sends comprehensive sensor data. Downstream crash detection systems analyze G-force patterns, speed changes, accelerometer spikes, and device state changes to identify potential crash events.

### Client-Side Integration

To consume the enhanced telemetry messages, your client applications need to handle the `EnhancedTelematicsMessage` format:

**Java Record Example:**
```java
public record EnhancedTelematicsMessage(
    @JsonProperty("policy_id") String policyId,
    @JsonProperty("vin") String vin,
    Instant timestamp,
    @JsonProperty("speed_mph") double speedMph,
    @JsonProperty("current_street") String currentStreet,
    @JsonProperty("g_force") double gForce,
    EnhancedSensorData sensors
) {}
```

**Key Integration Points:**
- Use `EnhancedTelematicsMessage` instead of legacy `TelematicsMessage` 
- Handle the enhanced sensor data structure with GPS metadata
- Access device information for additional crash context
- Utilize street name information for location-based analysis
- Leverage barometric pressure for environmental crash factors

**Message Consumer Example:**
```java
@RabbitListener(queues = "telematics_work_queue.crash-detection-group")
public void processTelematicsData(EnhancedTelematicsMessage message) {
    // Access enhanced data
    String location = message.currentStreet();
    double altitude = message.sensors().gps().altitude();
    boolean deviceCharging = message.sensors().device().charging();
    
    // Your crash detection logic here
    if (message.gForce() > 4.0) {
        handlePotentialCrash(message);
    }
}
```

## Web Dashboard

Access the real-time dashboard at http://localhost:8082 when running locally.

### What you’ll see
- 🗺️ Interactive map with live driver markers
- 🧭 Legend + filters (Driving / Parked / Crash) overlay
- 🎛️ Controls: Trigger Crash, Pause/Resume, message rate slider
- 📊 Side stats: Active, Driving, Parked, Crashes
- ⚡ Event feed: key events like crash triggers

### Controls
- 🎯 Fit to All: zoom map to all active drivers
- 👣 Follow Selected: auto-pan to the chosen driver
- ⏸️ Pause/▶️ Resume Generation: toggle simulation without stopping the app
- ⏱️ Message Rate: tune interval (200–2000 ms)

## Testing

```bash
# Run all tests
mvn test

# Run with test profile
mvn test -Dspring.profiles.active=test
```

## Control Script Options

The `imc-telematics-gen.sh` script provides comprehensive application lifecycle management:

```bash
# Start the application (includes RabbitMQ setup)
./imc-telematics-gen.sh --start

# Stop the application gracefully
./imc-telematics-gen.sh --stop

# Restart the application
./imc-telematics-gen.sh --restart

# Check current status
./imc-telematics-gen.sh --status

# Tail application logs in real-time
./imc-telematics-gen.sh --logs

# Show help/usage
./imc-telematics-gen.sh --help
```

**Script Features:**
- **Automatic Dependencies**: Starts RabbitMQ container if not running
- **Health Verification**: Waits for application to be healthy before reporting success
- **PID Management**: Tracks process ID for reliable start/stop operations
- **Graceful Shutdown**: Uses Spring Boot actuator endpoint for clean shutdown
- **Log Management**: Streams all output to `logs/imc-telematics-gen.log`
- **Status Monitoring**: Shows application status, health, uptime, and dependencies

## Monitoring

The application includes Spring Boot Actuator endpoints:
- Health: `/actuator/health`
- Metrics: `/actuator/metrics` 
- Info: `/actuator/info`
- Shutdown: `/actuator/shutdown` (enabled for graceful shutdown)

## Stopping the Application

**Using Control Script (Recommended):**
```bash
./imc-telematics-gen.sh --stop
```

**Manual Shutdown:**
- Press `Ctrl+C` to gracefully stop the simulation
- Or use the "Stop Application" button in the web dashboard
- Or call the actuator endpoint: `curl -X POST http://localhost:8082/actuator/shutdown`

## Driver Behavior Simulation

### Driver States
- **DRIVING**: Normal driving with realistic speed and GPS movement
- **PARKED**: Stationary with zero speed and minimal accelerometer readings
- **POST_CRASH_IDLE**: Sits still for 10+ minutes after a crash event
- **TRAFFIC_STOP**: Brief stops (30-90 seconds) for traffic lights, etc.
- **BREAK_TIME**: Longer stops (5+ minutes) for breaks, gas, etc.

### Realistic Behaviors
- Drivers start at random points along real Atlanta street routes
- Drivers follow actual road networks with proper GPS coordinates
- While driving, 5% chance per message to stop for various reasons
- Crash events trigger immediate transition to POST_CRASH_IDLE state
- Each driver has individual location, speed, and message tracking
- GPS coordinates follow real street paths with realistic movement patterns
- Speed limits and traffic controls based on actual route data

## Customization

Modify `application.yml` or set environment variables to customize:
- **Driver count** (`telematics.simulation.driver-count`)
- **Message frequency** (`telematics.simulation.interval-ms`)
- **Crash frequency** (`telematics.simulation.crash-frequency`)
- **Post-crash idle time** (`telematics.behavior.post-crash-idle-minutes`)
- **Random stop probability** (`telematics.behavior.random-stop-probability`)
- **Break duration** (`telematics.behavior.break-duration-minutes`)
- **Base location** (`telematics.location.latitude/longitude`)
- **Base policy ID** (`telematics.policy.id`)
