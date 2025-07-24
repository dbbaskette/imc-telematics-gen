#!/bin/bash

set -e

# Check for --clean flag
CLEAN_ONLY=false
if [[ "$1" == "--clean" ]]; then
    CLEAN_ONLY=true
fi

echo "🚗 ACME Insurance Telematics Generator"
echo "======================================"
if [ "$CLEAN_ONLY" = true ]; then
    echo "🧹 CLEAN MODE - Stopping old processes only"
fi
echo ""

# Function to kill any existing Spring Boot processes
kill_existing_processes() {
    echo "🔄 Checking for existing Spring Boot processes..."
    
    # Kill any existing spring-boot:run processes
    if pgrep -f "spring-boot:run" > /dev/null 2>&1; then
        echo "🛑 Stopping existing Spring Boot processes..."
        pkill -f "spring-boot:run" || true
        sleep 3
        
        # Force kill if still running
        if pgrep -f "spring-boot:run" > /dev/null 2>&1; then
            echo "🛑 Force stopping remaining processes..."
            pkill -9 -f "spring-boot:run" || true
            sleep 2
        fi
        echo "✅ Existing processes stopped"
    else
        echo "✅ No existing Spring Boot processes found"
    fi
}

# Function to check if Docker is running
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        echo "❌ Docker is not running. Please start Docker Desktop and try again."
        exit 1
    fi
    echo "✅ Docker is running"
}

# Function to check if RabbitMQ container exists and is running
check_rabbitmq() {
    if docker ps --format "table {{.Names}}" | grep -q "^rabbitmq$"; then
        echo "✅ RabbitMQ container is already running"
        return 0
    elif docker ps -a --format "table {{.Names}}" | grep -q "^rabbitmq$"; then
        echo "🔄 Starting existing RabbitMQ container..."
        docker start rabbitmq
        return 0
    else
        return 1
    fi
}

# Function to start RabbitMQ container
start_rabbitmq() {
    echo "🐰 Starting RabbitMQ container..."
    docker run -d \
        --name rabbitmq \
        -p 5672:5672 \
        -p 15672:15672 \
        rabbitmq:3-management-alpine
    
    echo "⏳ Waiting for RabbitMQ to be ready..."
    sleep 10
    
    # Wait for RabbitMQ to be ready
    max_attempts=30
    attempt=1
    while [ $attempt -le $max_attempts ]; do
        if docker exec rabbitmq rabbitmqctl status > /dev/null 2>&1; then
            echo "✅ RabbitMQ is ready!"
            break
        fi
        echo "⏳ Attempt $attempt/$max_attempts - waiting for RabbitMQ..."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    if [ $attempt -gt $max_attempts ]; then
        echo "❌ RabbitMQ failed to start within expected time"
        exit 1
    fi
}

# Function to cleanup on script exit
cleanup() {
    echo ""
    echo "🛑 Shutting down..."
    
    # Kill any Spring Boot processes
    if pgrep -f "spring-boot:run" > /dev/null 2>&1; then
        echo "🛑 Stopping Spring Boot processes..."
        pkill -f "spring-boot:run" || true
        sleep 2
    fi
    
    if [ "$RABBITMQ_STARTED" = "true" ]; then
        echo "🔄 Stopping and removing RabbitMQ container..."
        docker stop rabbitmq > /dev/null 2>&1 || true
        docker rm rabbitmq > /dev/null 2>&1 || true
        echo "✅ RabbitMQ container stopped and removed"
    else
        echo "💡 RabbitMQ container was already running - leaving it running"
    fi
    echo "👋 Goodbye!"
}

# Set up trap to cleanup on exit
trap cleanup EXIT INT TERM

# Main execution
echo "🔍 Checking prerequisites..."
kill_existing_processes

# If clean mode, also stop RabbitMQ and exit
if [ "$CLEAN_ONLY" = true ]; then
    echo "🔄 Stopping RabbitMQ containers..."
    if docker ps --format "table {{.Names}}" | grep -q "^rabbitmq$"; then
        echo "🛑 Stopping RabbitMQ container..."
        docker stop rabbitmq > /dev/null 2>&1 || true
        docker rm rabbitmq > /dev/null 2>&1 || true
        echo "✅ RabbitMQ container stopped and removed"
    else
        echo "✅ No RabbitMQ container found"
    fi
    
    # Also clean up any old telemetrics processes
    if pgrep -f "telematics" > /dev/null 2>&1; then
        echo "🛑 Stopping remaining telematics processes..."
        pkill -f "telematics" || true
        sleep 2
        echo "✅ Telematics processes stopped"
    fi
    
    echo "🧹 Cleanup complete!"
    exit 0
fi

check_docker

RABBITMQ_STARTED="false"
if ! check_rabbitmq; then
    start_rabbitmq
    RABBITMQ_STARTED="true"
fi

echo ""
echo "📊 RabbitMQ Management UI: http://localhost:15672"
echo "   Username: guest"
echo "   Password: guest"
echo "📡 Monitor queue: telematics_stream"
echo ""
echo "🗺️  Web Dashboard: http://localhost:8082"
echo "   Real-time Atlanta map with driver tracking"
echo ""
echo "🚀 Starting telematics generator..."
echo "   Press Ctrl+C to stop the simulation"
echo ""

# Set up configuration from template if needed
if [ ! -f "src/main/resources/application.yml" ]; then
    echo "📝 Creating application.yml from template..."
    cp src/main/resources/application.yml.template src/main/resources/application.yml
fi

# Set the active profile to local
export SPRING_PROFILES_ACTIVE=local

# Run the Spring Boot application
mvn spring-boot:run