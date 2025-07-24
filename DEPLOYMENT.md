# Deployment Guide

## Local Development

### Prerequisites
```bash
# Start RabbitMQ with Docker
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management

# Or install locally on macOS
brew install rabbitmq
brew services start rabbitmq
```

### Run Application
```bash
# Option 1: Using Maven
mvn spring-boot:run

# Option 2: Using the provided script
./run-local.sh

# Option 3: Build and run JAR
mvn clean package
java -jar target/telematics-generator-0.0.1-SNAPSHOT.jar
```

### Monitor Messages
- RabbitMQ Management UI: http://localhost:15672 (guest/guest)
- Queue name: `telematics_stream`

## Cloud Foundry Deployment

### Prerequisites
- CF CLI installed and logged in
- RabbitMQ service available in your CF marketplace

### Deploy Steps
```bash
# 1. Create RabbitMQ service instance
cf create-service rabbitmq-service-plan my-rabbitmq

# 2. Build the application
mvn clean package

# 3. Create manifest.yml
cat > manifest.yml << EOF
applications:
- name: telematics-generator
  path: target/telematics-generator-0.0.1-SNAPSHOT.jar
  memory: 1G
  instances: 1
  env:
    SPRING_PROFILES_ACTIVE: cloud
  services:
  - my-rabbitmq
EOF

# 4. Push to Cloud Foundry
cf push
```

### Environment Variables (Cloud)
- `RABBITMQ_HOST`: RabbitMQ host (auto-configured via service binding)
- `RABBITMQ_PORT`: RabbitMQ port (default: 5672)
- `RABBITMQ_USERNAME`: Username (auto-configured)
- `RABBITMQ_PASSWORD`: Password (auto-configured)
- `TELEMATICS_QUEUE_NAME`: Queue name (default: telematics_stream)
- `POLICY_ID`: Policy identifier (default: ACME-AUTO-98765)
- `VEHICLE_LATITUDE`: Starting GPS latitude (default: 40.7128)
- `VEHICLE_LONGITUDE`: Starting GPS longitude (default: -74.0060)
- `SIMULATION_INTERVAL_MS`: Milliseconds between messages (default: 1500)
- `CRASH_FREQUENCY`: Messages between crash events (default: 20)

## Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests (with Testcontainers)
```bash
# Requires Docker running
mvn test -Dtest=TelematicsIntegrationTest
```

## Monitoring

### Application Health
```bash
curl http://localhost:8080/actuator/health
```

### Metrics
```bash
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

## Troubleshooting

### Common Issues

1. **RabbitMQ Connection Failed**
   ```
   Solution: Ensure RabbitMQ is running and accessible
   Local: docker ps | grep rabbitmq
   Cloud: cf services | grep rabbitmq
   ```

2. **Application Won't Start**
   ```
   Check Java version: java -version (requires Java 21+)
   Check logs: cf logs telematics-generator --recent
   ```

3. **No Messages in Queue**
   ```
   Check queue exists and application has permissions
   Verify queue name configuration
   Check application logs for errors
   ```

### Log Levels
```yaml
logging:
  level:
    com.acme.insurance.telematics: DEBUG
    org.springframework.amqp: WARN
```