#!/bin/bash
# Webpage Accessibility and API Endpoint Tests
#
# Purpose: Verifies that all expected web routes and API endpoints return valid responses
# Significance: Ensures basic functionality and accessibility of all pages/routes in BookLore
# Key Functions:
# - Tests public endpoints that should be accessible without authentication
# - Tests protected endpoints to verify proper authentication requirements
# - Validates HTTP status codes for different routes
# - Confirms server connectivity and general health
# - Provides basic smoke test for entire application surface
#
# Requirements:
# - Running BookLore instance accessible via BASE_URL
# - Network access to test server

set -e

BASE_URL="${BASE_URL:-http://localhost:6060}"
TIMEOUT="${TIMEOUT:-10}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

ENDPOINTS=(
    "/|200|Homepage"
    "/api/v1/version|200|Version API"
    "/actuator/health|200|Health Check"
    "/actuator/info|200|Application Info"
)

PROTECTED_ENDPOINTS=(
    "/api/v1/books|401,403|Books API (protected)"
    "/api/v1/libraries|401,403|Libraries API (protected)"
    "/api/v1/users|401,403|Users API (protected)"
)

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; return 0; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; return 0; }
log_error() { echo -e "${RED}[ERROR]${NC} $1" >&2; return 1; }
log_success() { echo -e "${GREEN}[PASS]${NC} $1"; return 0; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1" >&2; return 1; }

test_url() {
    local url="$1"
    local expected="$2"
    local description="$3"
    
    local status_code
    status_code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout "$TIMEOUT" "$url" 2>/dev/null || echo "000")
    IFS=',' read -ra EXPECTED_CODES <<< "$expected"
    for code in "${EXPECTED_CODES[@]}"; do
        if [[ "$status_code" = "$code" ]]; then
            log_success "$description: $url (HTTP $status_code)"
            return 0
        fi
    done
    
    log_fail "$description: $url - Expected HTTP $expected, got $status_code"
    return 1
}

main() {
    local failed=0
    local passed=0
    local skipped=0
    
    log_info "=========================================="
    log_info "BookLore Webpage Accessibility Tests"
    log_info "Base URL: $BASE_URL"
    log_info "=========================================="
    
    log_info "Checking server connectivity..."
    if ! curl -s --connect-timeout 5 "$BASE_URL" >/dev/null 2>&1; then
        log_error "Cannot connect to $BASE_URL"
        log_info "Make sure the application is running"
        exit 1
    fi
    log_success "Server is reachable"
    
    log_info ""
    log_info "Testing public endpoints..."
    for entry in "${ENDPOINTS[@]}"; do
        IFS='|' read -r endpoint expected description <<< "$entry"
        if test_url "${BASE_URL}${endpoint}" "$expected" "$description"; then
            passed=$((passed + 1))
        else
            failed=$((failed + 1))
        fi
    done
    
    log_info ""
    log_info "Testing protected endpoints..."
    for entry in "${PROTECTED_ENDPOINTS[@]}"; do
        IFS='|' read -r endpoint expected description <<< "$entry"
        if test_url "${BASE_URL}${endpoint}" "$expected" "$description"; then
            passed=$((passed + 1))
        else
            failed=$((failed + 1))
        fi
    done
    
    log_info ""
    log_info "=========================================="
    log_info "Test Results Summary"
    log_info "=========================================="
    log_info "Passed:  $passed"
    log_info "Failed:  $failed"
    log_info "Skipped: $skipped"
    log_info "=========================================="
    
    if [[ $failed -eq 0 ]]; then
        log_success "All tests passed!"
        exit 0
    else
        log_fail "$failed test(s) failed"
        exit 1
    fi
}

while [[ $# -gt 0 ]]; do
    case $1 in
        -u|--url)
            BASE_URL="$2"
            shift 2
            ;;
        -t|--timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -u, --url URL       Base URL (default: http://localhost:6060)"
            echo "  -t, --timeout SECS  Connection timeout (default: 10)"
            echo "  -h, --help          Show this help message"
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

main
