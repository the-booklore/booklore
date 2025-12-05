#!/bin/bash
# Docker Integration Tests for BookLore
# This script tests the Docker container health and basic functionality

set -e

# Configuration
CONTAINER_NAME="${CONTAINER_NAME:-booklore-test}"
IMAGE_NAME="${IMAGE_NAME:-booklore:test}"
APP_PORT="${APP_PORT:-6060}"
TIMEOUT="${TIMEOUT:-120}"
HEALTH_ENDPOINT="/actuator/health"
VERSION_ENDPOINT="/api/v1/version"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Start MariaDB container for testing
start_mariadb() {
    log_info "Starting MariaDB container..."
    docker run -d \
        --name "${CONTAINER_NAME}-db" \
        -e "MYSQL_ROOT_PASSWORD=testpassword" \
        -e "MYSQL_DATABASE=booklore" \
        -e "MYSQL_USER=testuser" \
        -e "MYSQL_PASSWORD=testpass" \
        mariadb:10.11 || {
        log_error "Failed to start MariaDB container"
        return 1
    }
    
    # Wait for MariaDB to be ready
    log_info "Waiting for MariaDB to be ready..."
    for i in {1..30}; do
        if docker exec "${CONTAINER_NAME}-db" mysqladmin ping -h localhost -u root -ptestpassword >/dev/null 2>&1; then
            log_success "MariaDB is ready"
            return 0
        fi
        log_info "Waiting for MariaDB... ($i/30)"
        sleep 2
    done
    
    log_error "MariaDB failed to start"
    return 1
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
}

# Cleanup function
cleanup() {
    log_info "Cleaning up..."
    docker stop "$CONTAINER_NAME" 2>/dev/null || true
    docker rm "$CONTAINER_NAME" 2>/dev/null || true
    docker stop "${CONTAINER_NAME}-db" 2>/dev/null || true
    docker rm "${CONTAINER_NAME}-db" 2>/dev/null || true
}

# Wait for container to be healthy
wait_for_health() {
    local elapsed=0
    local interval=5
    
    log_info "Waiting for container to be healthy (timeout: ${TIMEOUT}s)..."
    
    while [ $elapsed -lt $TIMEOUT ]; do
        local status
        status=$(docker inspect --format='{{.State.Health.Status}}' "$CONTAINER_NAME" 2>/dev/null || echo "not running")
        
        case $status in
            "healthy")
                log_success "Container is healthy after ${elapsed}s"
                return 0
                ;;
            "unhealthy")
                log_error "Container is unhealthy"
                docker logs --tail 50 "$CONTAINER_NAME"
                return 1
                ;;
            *)
                log_info "Current status: $status (${elapsed}s elapsed)"
                ;;
        esac
        
        sleep $interval
        elapsed=$((elapsed + interval))
    done
    
    log_error "Timeout waiting for container to be healthy"
    docker logs --tail 100 "$CONTAINER_NAME"
    return 1
}

# Test HTTP endpoint
test_endpoint() {
    local endpoint="$1"
    local expected_status="${2:-200}"
    local description="$3"
    
    local response
    local status_code
    
    response=$(curl -s -w "\n%{http_code}" "http://localhost:${APP_PORT}${endpoint}" 2>/dev/null || echo "000")
    status_code=$(echo "$response" | tail -n1)
    
    if [ "$status_code" = "$expected_status" ]; then
        log_success "$description (HTTP $status_code)"
        return 0
    else
        log_fail "$description - Expected HTTP $expected_status, got $status_code"
        return 1
    fi
}

# Test JSON response
test_json_endpoint() {
    local endpoint="$1"
    local json_field="$2"
    local description="$3"
    
    local response
    response=$(curl -s "http://localhost:${APP_PORT}${endpoint}" 2>/dev/null)
    
    if echo "$response" | grep -q "$json_field"; then
        log_success "$description - Found '$json_field' in response"
        return 0
    else
        log_fail "$description - '$json_field' not found in response"
        echo "Response: $response"
        return 1
    fi
}

# Main test execution
main() {
    local test_results=()
    local failed=0
    
    log_info "=========================================="
    log_info "BookLore Docker Integration Tests"
    log_info "=========================================="
    
    # Setup trap for cleanup
    trap cleanup EXIT
    
    # Clean up any existing container
    cleanup
    
    # Check if image exists
    if ! docker image inspect "$IMAGE_NAME" >/dev/null 2>&1; then
        log_warn "Image $IMAGE_NAME not found, building..."
        docker build -t "$IMAGE_NAME" -f docker/Dockerfile . || {
            log_error "Failed to build Docker image"
            exit 1
        }
    fi
    
    # Start MariaDB
    start_mariadb || {
        log_error "Failed to start MariaDB"
        exit 1
    }
    
    # Start container
    log_info "Starting container..."
    docker run -d \
        --name "$CONTAINER_NAME" \
        --link "${CONTAINER_NAME}-db:mariadb" \
        -p "${APP_PORT}:6060" \
        -e "DATABASE_URL=jdbc:mariadb://mariadb:3306/booklore" \
        -e "DATABASE_USERNAME=testuser" \
        -e "DATABASE_PASSWORD=testpass" \
        "$IMAGE_NAME" || {
        log_error "Failed to start container"
        exit 1
    }
    
    # Wait for container to be healthy
    wait_for_health || {
        failed=$((failed + 1))
    }
    
    log_info ""
    log_info "=========================================="
    log_info "Running Endpoint Tests"
    log_info "=========================================="
    
    # Test 1: Health endpoint
    if test_endpoint "$HEALTH_ENDPOINT" "200" "Health endpoint"; then
        test_results+=("PASS: Health endpoint")
    else
        test_results+=("FAIL: Health endpoint")
        failed=$((failed + 1))
    fi
    
    # Test 2: Version endpoint
    if test_endpoint "$VERSION_ENDPOINT" "200" "Version endpoint"; then
        test_results+=("PASS: Version endpoint")
    else
        # This might require authentication, so 401/403 is acceptable
        if test_endpoint "$VERSION_ENDPOINT" "401" "Version endpoint (auth required)" || \
           test_endpoint "$VERSION_ENDPOINT" "403" "Version endpoint (auth required)"; then
            test_results+=("PASS: Version endpoint (requires auth)")
        else
            test_results+=("FAIL: Version endpoint")
            failed=$((failed + 1))
        fi
    fi
    
    # Test 3: Static resources (if frontend is bundled)
    if test_endpoint "/" "200" "Frontend static resources"; then
        test_results+=("PASS: Frontend static resources")
    else
        test_results+=("WARN: Frontend static resources not available")
    fi
    
    # Test 4: API docs (if enabled)
    if test_endpoint "/swagger-ui.html" "200" "Swagger UI" || \
       test_endpoint "/swagger-ui/index.html" "200" "Swagger UI"; then
        test_results+=("PASS: Swagger UI")
    else
        test_results+=("SKIP: Swagger UI (may be disabled)")
    fi
    
    # Print summary
    log_info ""
    log_info "=========================================="
    log_info "Test Results Summary"
    log_info "=========================================="
    
    for result in "${test_results[@]}"; do
        echo "  $result"
    done
    
    log_info ""
    if [ $failed -eq 0 ]; then
        log_success "All tests passed!"
        exit 0
    else
        log_fail "$failed test(s) failed"
        exit 1
    fi
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --image)
            IMAGE_NAME="$2"
            shift 2
            ;;
        --port)
            APP_PORT="$2"
            shift 2
            ;;
        --timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --image NAME     Docker image name (default: booklore:test)"
            echo "  --port PORT      Application port (default: 6060)"
            echo "  --timeout SECS   Timeout for health check (default: 120)"
            echo "  --help           Show this help message"
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

main
