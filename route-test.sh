#!/bin/bash

# Configuration
GATEWAY1="http://localhost:8080"
TIMEOUT=5  # Timeout for curl requests in seconds

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

# Test status tracking
TESTS_TOTAL=0
TESTS_PASSED=0
TESTS_FAILED=0

# Helper function for formatted output
print_header() {
    echo -e "\n${BLUE}=== $1 ===${NC}"
}

print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓ $2${NC}"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗ $2${NC}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
    TESTS_TOTAL=$((TESTS_TOTAL + 1))
}

# Function to check if gateway is available
check_gateway_health() {
    print_header "Checking Gateway Health"
    if curl -s -f -m $TIMEOUT "$GATEWAY1/actuator/health" > /dev/null; then
        print_result 0 "Gateway is healthy"
        return 0
    else
        print_result 1 "Gateway is not responding"
        return 1
    fi
}

# Function to test adding a route
test_add_route() {
    local route_id=$1
    local path=$2
    local target_uri=$3
    
    print_header "Adding Route: $route_id"
    
    local response=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY1/routes" \
        -H "Content-Type: application/json" \
        -d "{
            \"id\": \"$route_id\",
            \"uri\": \"$target_uri\",
            \"predicates\": [
                {
                    \"name\": \"Path\",
                    \"args\": [\"$path\"]
                }
            ],
            \"filters\": [
                {
                    \"name\": \"StripPrefix\",
                    \"args\": [\"1\"]
                }
            ]
        }")

    local status_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')

    if [ "$status_code" -eq 200 ]; then
        print_result 0 "Route $route_id added successfully"
    else
        print_result 1 "Failed to add route $route_id (Status: $status_code)"
        echo -e "${RED}Response: $body${NC}"
    fi
}

# Function to verify route exists
verify_route_exists() {
    local route_id=$1
    print_header "Verifying Route: $route_id"
    
    local routes=$(curl -s "$GATEWAY1/routes")
    if echo "$routes" | jq -e ".[] | select(.id == \"$route_id\")" > /dev/null; then
        print_result 0 "Route $route_id exists"
        return 0
    else
        print_result 1 "Route $route_id not found"
        return 1
    fi
}

# Function to test deleting a route
test_delete_route() {
    local route_id=$1
    print_header "Deleting Route: $route_id"
    
    local response=$(curl -s -w "\n%{http_code}" -X DELETE "$GATEWAY1/routes/$route_id")
    local status_code=$(echo "$response" | tail -n1)
    
    if [ "$status_code" -eq 200 ]; then
        print_result 0 "Route $route_id deleted successfully"
    else
        print_result 1 "Failed to delete route $route_id (Status: $status_code)"
    fi
}

# Function to get all routes
get_all_routes() {
    print_header "Getting All Routes"
    local routes=$(curl -s "$GATEWAY1/routes")
    echo -e "${YELLOW}Current Routes:${NC}"
    echo "$routes" | jq '.'
}

# Function to test route functionality
test_route_functionality() {
    local route_id=$1
    local test_path=$2
    print_header "Testing Route Functionality: $route_id"
    
    local response=$(curl -s -w "\n%{http_code}" "$GATEWAY1$test_path")
    local status_code=$(echo "$response" | tail -n1)
    
    if [ "$status_code" -lt 500 ]; then
        print_result 0 "Route $route_id is functioning (Status: $status_code)"
    else
        print_result 1 "Route $route_id test failed (Status: $status_code)"
    fi
}

# Main test sequence
echo -e "${BLUE}Starting Gateway Route Management Tests${NC}"
echo -e "${BLUE}=====================================${NC}"

# Check gateway health
check_gateway_health || exit 1

# Initial route status
get_all_routes

# Test adding routes
test_add_route "test-api-1" "/api1/**" "https://api1.example.com"
sleep 1
verify_route_exists "test-api-1"

test_add_route "test-api-2" "/api2/**" "https://api2.example.com"
sleep 1
verify_route_exists "test-api-2"

# Get routes after adding
get_all_routes

# Test route functionality
test_route_functionality "test-api-1" "/api1/test"
test_route_functionality "test-api-2" "/api2/test"

# Test deleting routes
test_delete_route "test-api-1"
sleep 1
test_delete_route "test-api-2"

# Final route check
get_all_routes

# Print test summary
echo -e "\n${BLUE}Test Summary${NC}"
echo -e "${BLUE}============${NC}"
echo -e "Total Tests: $TESTS_TOTAL"
echo -e "Passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Failed: ${RED}$TESTS_FAILED${NC}"

# Exit with status based on test results
[ $TESTS_FAILED -eq 0 ]
