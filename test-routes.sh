#!/bin/bash
# test-routes.sh

# Base URLs for both gateway instances
GATEWAY1="http://localhost:8080"
GATEWAY2="http://localhost:8082"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "Testing Gateway Route Management"
echo "-------------------------------"

# Function to test adding a route
test_add_route() {
    local gateway=$1
    echo -e "\n${GREEN}Adding test route to $gateway${NC}"
    
    curl -X POST "$gateway/routes" \
    -H "Content-Type: application/json" \
    -d '{
        "id": "test_api_route",
        "uri": "https://api.example.com",
        "predicates": [
            {
                "name": "Path",
                "args": ["/test/**"]
            }
        ],
        "filters": [
            {
                "name": "StripPrefix",
                "args": ["1"]
            }
        ]
    }'
    
    echo -e "\n${GREEN}Checking if route was added on both gateways:${NC}"
    curl -s "$GATEWAY1/routes" | jq .
    curl -s "$GATEWAY2/routes" | jq .
}

# Function to test deleting a route
test_delete_route() {
    local gateway=$1
    local route_id=$2
    echo -e "\n${GREEN}Deleting route $route_id from $gateway${NC}"
    
    curl -X DELETE "$gateway/routes/$route_id"
    
    echo -e "\n${GREEN}Checking if route was deleted on both gateways:${NC}"
    curl -s "$GATEWAY1/routes" | jq .
    curl -s "$GATEWAY2/routes" | jq .
}

# Function to get all routes
get_all_routes() {
    local gateway=$1
    echo -e "\n${GREEN}Getting all routes from $gateway${NC}"
    curl -s "$gateway/routes" | jq .
}

# Main test sequence
echo "1. Getting initial routes from both gateways"
get_all_routes $GATEWAY1
get_all_routes $GATEWAY2

echo -e "\n2. Adding a test route through Gateway 1"
test_add_route $GATEWAY1

sleep 2  # Wait for synchronization

echo -e "\n3. Adding another route through Gateway 2"
test_add_route $GATEWAY2

sleep 2  # Wait for synchronization

echo -e "\n4. Deleting a route through Gateway 1"
test_delete_route $GATEWAY1 "test_api_route"

sleep 2  # Wait for synchronization

echo -e "\n5. Final route check on both gateways"
get_all_routes $GATEWAY1
get_all_routes $GATEWAY2
