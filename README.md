# Insurance Megacorp Telematics Generator

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

### Normal Driving Data
```json
{
  "policy_id": "IMC-98675",
  "timestamp": "2024-01-15T10:30:45.123Z",
  "speed_mph": 32.5,
  "sensors": {
    "gps": {
      "lat": 33.7701,
      "lon": -84.3876
    },
    "accelerometer": {
      "x": 0.1234,
      "y": -0.0567,
      "z": 0.9876
    }
  },
  "g_force": 0.12
}
```

### High G-Force Event Data (Potential Crash)
```json
{
  "policy_id": "IMC-98680",
  "timestamp": "2024-01-15T10:30:45.123Z", 
  "speed_mph": 0.0,
  "sensors": {
    "gps": {
      "lat": 33.7701,
      "lon": -84.3876
    },
    "accelerometer": {
      "x": 6.5432,
      "y": 5.8901,
      "z": 1.2345
    }
  },
  "g_force": 8.67
}
```

**Raw Sensor Data**: The telematics device only sends raw sensor readings. Downstream processing systems analyze the G-force, speed changes, and accelerometer data to detect crash events.

## Web Dashboard

Access the real-time dashboard at http://localhost:8082 when running locally.

### Features
- **Interactive Map**: Real-time Atlanta street map showing all active drivers
- **Driver Icons**: Different colored icons for each driver state:
  - 🚗 **Green**: DRIVING 
  - 🅿️ **Blue**: PARKED
  - 🚨 **Red**: CRASHED (POST_CRASH_IDLE)
  - 🚦 **Yellow**: TRAFFIC_STOP
  - ☕ **Orange**: BREAK_TIME

### Dashboard Controls
- **Driver Selection**: Dropdown to focus on specific driver
- **Trigger Crash**: Button to manually trigger crash events for testing
- **Stop Application**: Red button to gracefully shutdown the application
- **Auto-refresh**: Real-time updates via WebSocket connection
- **Driver Info**: Displays current state, speed, street location for selected driver

### Real-time Updates
The dashboard receives live updates through WebSocket connections, showing:
- Driver positions moving along real Atlanta streets
- State changes (parking, crashes, traffic stops)
- Speed and location information
- Route progress for each driver

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