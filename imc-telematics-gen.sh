#!/bin/bash

#
# IMC Telematics Generator Control Script
# Provides clean start/stop/status functionality for the telematics simulation
#

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_NAME="imc-telematics-gen"
PID_FILE="${SCRIPT_DIR}/.${APP_NAME}.pid"
LOG_FILE="${SCRIPT_DIR}/logs/${APP_NAME}.log"
HEALTH_CHECK_URL="http://localhost:8082/actuator/health"
MAX_STARTUP_WAIT=60
MAX_SHUTDOWN_WAIT=30

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Create logs directory if it doesn't exist
mkdir -p "${SCRIPT_DIR}/logs"

# Function to check if RabbitMQ is running
check_rabbitmq() {
    if ! docker ps --format "table {{.Names}}" | grep -q "rabbitmq-telematics"; then
        log_info "Starting RabbitMQ container..."
        docker run -d \
            --name rabbitmq-telematics \
            --hostname rabbitmq-host \
            -p 5672:5672 \
            -p 15672:15672 \
            -e RABBITMQ_DEFAULT_USER=guest \
            -e RABBITMQ_DEFAULT_PASS=guest \
            rabbitmq:3.13-management-alpine
        
        # Wait for RabbitMQ to be ready
        log_info "Waiting for RabbitMQ to be ready..."
        sleep 10
        
        # Verify RabbitMQ is accessible
        for i in {1..30}; do
            if curl -s -u guest:guest http://localhost:15672/api/whoami >/dev/null 2>&1; then
                log_success "RabbitMQ is ready"
                return 0
            fi
            sleep 1
        done
        log_error "RabbitMQ failed to start properly"
        return 1
    else
        log_info "RabbitMQ container is already running"
    fi
}

# Function to get process status
get_status() {
    if [[ -f "$PID_FILE" ]]; then
        local pid=$(cat "$PID_FILE")
        if ps -p "$pid" > /dev/null 2>&1; then
            echo "running"
            return 0
        else
            # PID file exists but process is dead
            rm -f "$PID_FILE"
            echo "stopped"
            return 1
        fi
    else
        echo "stopped"
        return 1
    fi
}

# Function to wait for application health
wait_for_health() {
    log_info "Waiting for application to become healthy..."
    local count=0
    while [[ $count -lt $MAX_STARTUP_WAIT ]]; do
        if curl -s "$HEALTH_CHECK_URL" | grep -q '"status":"UP"'; then
            log_success "Application is healthy and ready"
            return 0
        fi
        sleep 1
        ((count++))
    done
    log_error "Application failed to become healthy within ${MAX_STARTUP_WAIT} seconds"
    return 1
}

# Function to start the application
start_app() {
    local current_status=$(get_status)
    
    if [[ "$current_status" == "running" ]]; then
        log_warning "Application is already running (PID: $(cat "$PID_FILE"))"
        return 0
    fi
    
    log_info "Starting $APP_NAME..."
    
    # Ensure RabbitMQ is running
    if ! check_rabbitmq; then
        log_error "Failed to start RabbitMQ dependency"
        return 1
    fi
    
    # Start the Spring Boot application in background with logging
    cd "$SCRIPT_DIR"
    log_info "Application output will be logged to: $LOG_FILE"
    
    # Use exec to avoid shell wrapper and get the actual Java process PID
    {
        echo "=== IMC Telematics Generator Started at $(date) ==="
        mvn spring-boot:run 2>&1
    } >> "$LOG_FILE" &
    local app_pid=$!
    
    # Save PID to file
    echo "$app_pid" > "$PID_FILE"
    log_info "Application started with PID: $app_pid"
    
    # Wait for application to become healthy
    if wait_for_health; then
        log_success "$APP_NAME started successfully"
        echo ""
        log_info "📋 Monitoring:"
        log_info "   Logs: $0 --logs"
        log_info "   Health: curl $HEALTH_CHECK_URL"
        log_info "   Web Dashboard: http://localhost:8082"
        echo ""
        log_info "📱 Use '$0 --stop' to shutdown cleanly"
        return 0
    else
        log_error "Application failed to start properly"
        stop_app
        return 1
    fi
}

# Function to stop the application
stop_app() {
    local current_status=$(get_status)
    
    if [[ "$current_status" == "stopped" ]]; then
        log_warning "Application is not running"
        return 0
    fi
    
    local pid=$(cat "$PID_FILE")
    log_info "Stopping $APP_NAME (PID: $pid)..."
    
    # Try graceful shutdown first via actuator endpoint
    if curl -s -X POST http://localhost:8082/actuator/shutdown >/dev/null 2>&1; then
        log_info "Sent graceful shutdown signal via actuator"
    else
        log_info "Actuator shutdown not available, using SIGTERM"
        kill -TERM "$pid" 2>/dev/null || true
    fi
    
    # Wait for graceful shutdown
    local count=0
    while [[ $count -lt $MAX_SHUTDOWN_WAIT ]]; do
        if ! ps -p "$pid" > /dev/null 2>&1; then
            log_success "Application stopped gracefully"
            rm -f "$PID_FILE"
            return 0
        fi
        sleep 1
        ((count++))
    done
    
    # Force kill if still running
    log_warning "Graceful shutdown timed out, forcing termination"
    kill -KILL "$pid" 2>/dev/null || true
    rm -f "$PID_FILE"
    log_success "Application stopped (forced)"
}

# Function to tail logs
tail_logs() {
    if [[ ! -f "$LOG_FILE" ]]; then
        log_error "Log file does not exist: $LOG_FILE"
        log_info "Start the application first with: $0 --start"
        return 1
    fi
    
    log_info "Tailing logs from: $LOG_FILE"
    log_info "Press Ctrl+C to stop tailing"
    echo ""
    tail -f "$LOG_FILE"
}

# Function to show status
show_status() {
    local status=$(get_status)
    
    echo "=== IMC Telematics Generator Status ==="
    echo "Application: $status"
    
    if [[ "$status" == "running" ]]; then
        local pid=$(cat "$PID_FILE")
        echo "PID: $pid"
        echo "Uptime: $(ps -o etime= -p "$pid" 2>/dev/null || echo "unknown")"
        
        # Check health endpoint
        if curl -s "$HEALTH_CHECK_URL" | grep -q '"status":"UP"'; then
            echo "Health: UP"
        else
            echo "Health: DOWN"
        fi
        
        # Check RabbitMQ
        if docker ps --format "table {{.Names}}" | grep -q "rabbitmq-telematics"; then
            echo "RabbitMQ: running"
        else
            echo "RabbitMQ: stopped"
        fi
        
        echo "Log file: $LOG_FILE"
        echo "Web Dashboard: http://localhost:8082"
    fi
    
    echo "======================================"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 {--start|--stop|--restart|--status|--logs}"
    echo ""
    echo "Commands:"
    echo "  --start    Start the telematics generator"
    echo "  --stop     Stop the telematics generator"
    echo "  --restart  Restart the telematics generator"
    echo "  --status   Show current status"
    echo "  --logs     Tail the application logs (Ctrl+C to stop)"
    echo ""
    echo "Examples:"
    echo "  $0 --start     # Start the application"
    echo "  $0 --stop      # Stop the application"
    echo "  $0 --status    # Check if running"
    echo "  $0 --logs      # Follow the logs in real-time"
}

# Main execution
case "${1:-}" in
    --start)
        start_app
        ;;
    --stop)
        stop_app
        ;;
    --restart)
        stop_app
        sleep 2
        start_app
        ;;
    --status)
        show_status
        ;;
    --logs)
        tail_logs
        ;;
    --help|-h|help)
        show_usage
        ;;
    *)
        log_error "Invalid or missing command"
        echo ""
        show_usage
        exit 1
        ;;
esac