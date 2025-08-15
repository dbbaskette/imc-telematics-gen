<div align="center">

<img src="docs/logo.svg" alt="IMC Telematics" width="720"/>

<h1>Insurance Megacorp Telematics Generator</h1>

<p>
  🚗 Realistic multi-driver telematics simulator • 🌐 Live map dashboard • 🐇 RabbitMQ JSON events
</p>

</div>

A Spring Boot application that simulates realistic vehicle telematics data using an **optimized flat JSON structure** for maximum performance. Sends continuous sensor data to RabbitMQ with periodic crash events.

## Features

- **Multi-Driver Simulation**: Simulates multiple drivers with individual behaviors and states
- **Realistic Driver States**: DRIVING, PARKED, POST_CRASH_IDLE, TRAFFIC_STOP, BREAK_TIME
- **Real Road Networks**: Drivers follow actual Atlanta street routes with proper GPS coordinates
- **Speed Limit Integration**: Real speed limits from route data included in telemetry events
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

## Performance Features

🚀 **Optimized Flat JSON Structure**
- **50-70% reduction** in object allocation
- **30-50% faster** JSON serialization/deserialization
- **Direct SQL mapping** for simplified database storage
- **Eliminated nested object traversal** for better performance
- **Zero transformation overhead** in message pipeline
- **Consistent numeric ID formatting** across all identifier fields

## Recent Improvements

### ✨ v2.0 - Performance & Consistency Overhaul
- **🔥 Flat JSON Architecture**: Complete migration from nested to flat structure
- **📈 Performance Boost**: 50-70% improvement in processing speed
- **🔧 ID Consistency**: Unified numeric ID format (`driver_id: "400018"`)
- **🚀 Zero Overhead**: Direct message generation without transformation
- **📊 SQL-Ready**: Direct database mapping for all fields

## Prerequisites

- Java 21 or higher
- Maven 3.6+
- Docker (for local development with automatic RabbitMQ setup)

## Quick Start

### Using the Control Script (Recommended)

```bash
# Start the application with all dependencies
./imc-telematics-gen.sh --start

# Rebuild then start (skips tests by default)
./imc-telematics-gen.sh --build --start

# Build with tests, then start
./imc-telematics-gen.sh --with-tests --build --start

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
- Chainable commands (e.g., `--build --start`); optional `--with-tests` to run tests during build

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
  - **Queue**: `telematics_work_queue.crash-detection-group` (default)
  - To use a different queue, set `telematics.queue.name` in `application.yml` or via env var `TELEMATICS_QUEUE_NAME`
- **Base Policy ID**: `IMC-AUTO-98765` (with sequential driver IDs)
- **Routes**: File-based loading from `src/main/resources/routes/`
- **Driver Count**: 3 (legacy/random mode). For file-based drivers, use `telematics.simulation.max-drivers`
- **Max Drivers**: `telematics.simulation.max-drivers` caps how many drivers from `drivers.json` are initialized (0 = no cap)
- **Simulation Interval**: 500ms
- **Crash Frequency**: 50 messages between crash events per driver
- **Minimum Crash G-Force**: `telematics.simulation.min-crash-gforce` (default 6.0) enforced for forced crashes
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

The application sends **flattened telemetry data** with comprehensive sensor information in JSON format to RabbitMQ. The flat structure provides optimal performance for downstream processing and direct SQL mapping.

### Flat Telemetry Message Structure (Optimized)
```json
{
  "policy_id": 200018,
  "vehicle_id": 300021,
  "vin": "1HGBH41JXMN109186",
  "event_time": "2024-01-15T10:30:45.123Z",
  "speed_mph": 32.5,
  "speed_limit_mph": 35,
  "current_street": "Peachtree Street",
  "g_force": 1.18,
  "driver_id": "400018",
  
  "gps_latitude": 33.7701,
  "gps_longitude": -84.3876,
  "gps_altitude": 351.59,
  "gps_speed": 14.5,
  "gps_bearing": 148.37,
  "gps_accuracy": 2.64,
  "gps_satellite_count": 11,
  "gps_fix_time": 150,
  
  "accelerometer_x": 0.1234,
  "accelerometer_y": -0.0567,
  "accelerometer_z": 0.9876,
  
  "gyroscope_x": 0.02,
  "gyroscope_y": -0.01,
  "gyroscope_z": 0.15,
  
  "magnetometer_x": 25.74,
  "magnetometer_y": -8.73,
  "magnetometer_z": 40.51,
  "magnetometer_heading": 148.37,
  
  "barometric_pressure": 1013.25,
  
  "device_battery_level": 82,
  "device_signal_strength": -63,
  "device_orientation": "portrait",
  "device_screen_on": false,
  "device_charging": true
}
```

### Crash Event Data (High G-Force)
```json
{
  "policy_id": 200034,
  "vehicle_id": 300038,
  "vin": "KNDJP3A57H7123456",
  "event_time": "2024-01-15T10:30:45.123Z",
  "speed_mph": 0.0,
  "speed_limit_mph": 25,
  "current_street": "Highland Street",
  "g_force": 8.67,
  "driver_id": "400034",
  
  "gps_latitude": 33.7701,
  "gps_longitude": -84.3876,
  "gps_altitude": 345.12,
  "gps_speed": 0.0,
  "gps_bearing": 148.37,
  "gps_accuracy": 2.1,
  "gps_satellite_count": 10,
  "gps_fix_time": 120,
  
  "accelerometer_x": 6.5432,
  "accelerometer_y": 5.8901,
  "accelerometer_z": 1.2345,
  
  "gyroscope_x": 2.45,
  "gyroscope_y": -1.89,
  "gyroscope_z": 0.67,
  
  "magnetometer_x": 28.91,
  "magnetometer_y": -12.45,
  "magnetometer_z": 38.67,
  "magnetometer_heading": 145.23,
  
  "barometric_pressure": 1012.8,
  
  "device_battery_level": 78,
  "device_signal_strength": -58,
  "device_orientation": "landscape",
  "device_screen_on": true,
  "device_charging": false
}
```

### Flat Data Fields

**Core Message Fields:**
- `policy_id` (int): Insurance policy identifier (e.g., 200018)
- `vehicle_id` (int): Internal vehicle identifier (e.g., 300021)
- `vin` (string): Vehicle Identification Number
- `event_time` (ISO 8601): Event timestamp
- `speed_mph` (number): Current vehicle speed in miles per hour
- `speed_limit_mph` (int): Current speed limit from route data
- `current_street` (string): Real street name from GPS location
- `g_force` (number): Calculated G-force from accelerometer data
- `driver_id` (string): Numeric driver identifier (e.g., "400018")

**GPS Data Fields (gps_*):**
- `gps_latitude` (number): Latitude coordinate
- `gps_longitude` (number): Longitude coordinate
- `gps_altitude` (number): Elevation in meters
- `gps_speed` (number): Speed in meters per second
- `gps_bearing` (number): Direction of travel in degrees
- `gps_accuracy` (number): GPS accuracy in meters
- `gps_satellite_count` (int): Number of GPS satellites in view
- `gps_fix_time` (long): Time to acquire GPS fix in milliseconds

**Accelerometer Data Fields (accelerometer_*):**
- `accelerometer_x` (number): X-axis acceleration
- `accelerometer_y` (number): Y-axis acceleration
- `accelerometer_z` (number): Z-axis acceleration

**Gyroscope Data Fields (gyroscope_*):**
- `gyroscope_x` (number): Angular velocity around X-axis (pitch)
- `gyroscope_y` (number): Angular velocity around Y-axis (roll)
- `gyroscope_z` (number): Angular velocity around Z-axis (yaw)

**Magnetometer Data Fields (magnetometer_*):**
- `magnetometer_x` (number): Magnetic field strength in X-axis (μT)
- `magnetometer_y` (number): Magnetic field strength in Y-axis (μT)
- `magnetometer_z` (number): Magnetic field strength in Z-axis (μT)
- `magnetometer_heading` (number): Magnetic compass heading in degrees

**Environmental Data:**
- `barometric_pressure` (number): Atmospheric pressure in hPa

**Device Metadata Fields (device_*):**
- `device_battery_level` (int): Device battery percentage (0-100)
- `device_signal_strength` (int): Cellular signal strength in dBm
- `device_orientation` (string): Device orientation (portrait/landscape/face_up)
- `device_screen_on` (boolean): Whether device screen is active
- `device_charging` (boolean): Whether device is charging

**Analysis Notes**: The telematics device sends comprehensive sensor data. Downstream crash detection systems analyze G-force patterns, speed changes, accelerometer spikes, and device state changes to identify potential crash events.

### Client-Side Integration

To consume the **flattened telemetry messages**, your client applications should handle the `FlatTelematicsMessage` format for optimal performance:

**Java Record Example:**
```java
public record FlatTelematicsMessage(
    @JsonProperty("policy_id") int policyId,
    @JsonProperty("vehicle_id") int vehicleId,
    @JsonProperty("vin") String vin,
    @JsonProperty("event_time") Instant eventTime,
    @JsonProperty("speed_mph") double speedMph,
    @JsonProperty("speed_limit_mph") int speedLimitMph,
    @JsonProperty("g_force") double gForce,
    @JsonProperty("driver_id") String driverId,
    @JsonProperty("current_street") String currentStreet,
    
    // GPS fields
    @JsonProperty("gps_latitude") double gpsLatitude,
    @JsonProperty("gps_longitude") double gpsLongitude,
    @JsonProperty("gps_altitude") double gpsAltitude,
    // ... additional flat fields
) {}
```

**Key Integration Benefits:**
- **50-70% faster processing** with direct field access
- **No nested object traversal** required
- **Direct SQL mapping** for database storage
- **Simplified parsing** and reduced memory allocation
- **Better performance** for real-time crash detection

**Message Consumer Example:**
```java
@RabbitListener(queues = "telematics_work_queue.crash-detection-group")
public void processTelematicsData(FlatTelematicsMessage message) {
    // Direct access to all data - no nesting!
    String location = message.currentStreet();
    double currentSpeed = message.speedMph();
    int speedLimit = message.speedLimitMph();
    double altitude = message.gpsAltitude();
    boolean deviceCharging = message.deviceCharging();
    
    // Speed violation detection
    if (currentSpeed > speedLimit + 5) {
        handleSpeedingViolation(message, currentSpeed - speedLimit);
    }
    
    // Crash detection logic
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
- 🚨 Trigger Accident: sends an immediate crash event to the same queue as normal telemetry and logs a detailed publisher line with VIN/street/speed/speed limit/G-force

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

# Build (skip tests by default) and start
./imc-telematics-gen.sh --build --start

# Run tests during build
./imc-telematics-gen.sh --with-tests --build --start

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
- **Speed limits dynamically updated from route data and included in telemetry events**
- Traffic controls based on actual route data

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
