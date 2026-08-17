#!/bin/bash
# This script updates an existing tournament on the server using the Tourney Sync API.
# usage: echo '<json-tourney>' | ./set.sh
# echo '{"id":123, "name":"Dummy", "startDate":20260515, "endDate":"20260516", "organizer":"", "ident":"IGNORE", "typ":"TableTennis"}' | ./set.sh


# Configuration
USER="robert"
# Application Password for WordPress
PASSWORD="x0CsysbewmNvehrtOEnCgsuT"
BASE_URL="http://localhost:8080/wp-json/tourney/v1"

# Check if JSON input is provided
if [ -t 0 ] && [ -z "$1" ]; then
    echo "Usage: echo '<json-tourney>' | $0"
    echo "   or: $0 '<json-tourney>'"
    exit 1
fi

INPUT_JSON=""
if [ -n "$1" ]; then
    INPUT_JSON="$1"
else
    INPUT_JSON=$(cat)
fi

# Extract ID from JSON
PAGE_ID=$(echo "$INPUT_JSON" | jq -r '.id')

if [ -z "$PAGE_ID" ] || [ "$PAGE_ID" == "0" ] || [ "$PAGE_ID" == "null" ]; then
    echo "Error: Invalid or missing ID in tournament JSON (must be <> 0)."
    exit 1
fi

echo "Updating tournament ID $PAGE_ID..."

# 1. Fetch current version from server
echo "Fetching current version..."
READ_RES=$(curl -s -u "$USER:$PASSWORD" "${BASE_URL}/read?postId=$PAGE_ID")
VERSION=$(echo "$READ_RES" | jq -r '.version // 0')

echo "Current server version: $VERSION"

# 2. Prepare Sync Payload
# We wrap the tourney JSON into a TourneySyncRequest
SYNC_PAYLOAD=$(jq -n --argjson v "$VERSION" --argjson t "$INPUT_JSON" '{
    version: ($v | tonumber),
    tourney: $t
}')

# 3. Perform Sync
echo "Sending sync request..."
RES=$(curl -s -X POST "${BASE_URL}/tourney-sync?postId=$PAGE_ID" \
     -u "$USER:$PASSWORD" \
     -H "Content-Type: application/json" \
     -d "$SYNC_PAYLOAD")

echo "Response from API:"
echo "$RES" | jq .
