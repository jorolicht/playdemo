#!/bin/bash

# Configuration
USER="robert"
# Application Password for WordPress
PASSWORD="x0CsysbewmNvehrtOEnCgsuT"
BASE_URL="http://localhost:8080/wp-json/tourney/v1"

echo "Testing Create Tourney API (New Payload Format)..."

# Create a JSON Tourney object (matching case class Tourney)
# Fields: id, name, organizer, startDate, endDate, ident, typ
NAME="Sommer-Cup 2026"
START_DATE=20260715
IDENT="C-TT-2026-S"

PAYLOAD=$(jq -n --arg name "$NAME" --argjson start $START_DATE --arg ident "$IDENT" '{
    wpId: 0,
    name: $name,
    organizer: "TTV Test",
    startDate: $start,
    endDate: $start,
    ident: $ident,
    typ: "TableTennis"
}')

echo -e "\n1. POST /create (Payload: $NAME, $START_DATE)"

RES=$(curl -s -X POST "${BASE_URL}/create" \
     -u "$USER:$PASSWORD" \
     -H "Content-Type: application/json" \
     -d "$PAYLOAD")

echo "Response from API:"
echo "$RES" | jq .

# Extract the PageId for follow-up update test
PAGE_ID=$(echo "$RES" | jq -r '.pageId')

if [ "$PAGE_ID" != "null" ] && [ -n "$PAGE_ID" ]; then
    echo -e "\nSuccessfully created/updated tourney with PageId: $PAGE_ID"
    
    # Update test (id remains 0 in payload because slug is the identifier for update)
    PAYLOAD_UPDATE=$(echo "$PAYLOAD" | jq '.name = "Sommer-Cup 2026 (Update)"')
    
    echo -e "\n2. POST /create (Updating existing tourney via slug match)"
    
    RES_UPDATE=$(curl -s -X POST "${BASE_URL}/create" \
         -u "$USER:$PASSWORD" \
         -H "Content-Type: application/json" \
         -d "$PAYLOAD_UPDATE")
    
    echo "$RES_UPDATE" | jq .
else
    echo -e "\nFailed to get PageId from response."
    exit 1
fi
