#!/bin/bash

# Configuration
USER="robert"
PASSWORD="x0CsysbewmNvehrtOEnCgsuT"
PLAY_URL="https://localhost/playsrv"
WP_URL="https://localhost/wp-json/tourney/v1"

if [ -z "$1" ]; then
    echo "Usage: $0 <clicktt-xml-file>"
    exit 1
fi

XML_FILE="$1"

if [ ! -f "$XML_FILE" ]; then
    echo "Error: File $XML_FILE not found."
    exit 1
fi

echo "1. Converting ClickTT XML to JSON via Play Server..."
TOURNEY_JSON=$(curl -s -X POST "${PLAY_URL}/helper/clicktt-to-json" \
    -H "Content-Type: application/xml" \
    --data-binary "@$XML_FILE")

if [[ "$TOURNEY_JSON" == *"failed"* ]] || [[ -z "$TOURNEY_JSON" ]]; then
    echo "Error during conversion: $TOURNEY_JSON"
    exit 1
fi

echo "Successfully converted to JSON."
echo "$TOURNEY_JSON"
#echo "$TOURNEY_JSON" | jq .

# 2. Create Tournament Post on WordPress
echo -e "\n2. Creating Tournament on WordPress..."
CREATE_RES=$(curl -s -X POST "${WP_URL}/create" \
    -u "$USER:$PASSWORD" \
    -H "Content-Type: application/json" \
    -d "$TOURNEY_JSON")

PAGE_ID=$(echo "$CREATE_RES" | jq -r '.pageId // empty')
WP_VERSION=$(echo "$CREATE_RES" | jq -r '.version // 1')
FULL_SLUG=$(echo "$CREATE_RES" | jq -r '.slug // empty')

if [ -z "$PAGE_ID" ] || [ "$PAGE_ID" == "null" ]; then
    echo "Error creating tournament: $CREATE_RES"
    exit 1
fi

echo "Tournament created: PageId=$PAGE_ID, Version=$WP_VERSION, Slug=$FULL_SLUG"

# 3. Synchronize Clubs
echo -e "\n3. Synchronizing Clubs..."
CLUBS=$(echo "$TOURNEY_JSON" | jq -c '.clubs')
CLUBS_PAYLOAD=$(jq -n --argjson v "$WP_VERSION" --argjson c "$CLUBS" '{version: ($v | tonumber), clubs: $c}')

curl -s -X POST "${WP_URL}/clubs-sync?postId=$PAGE_ID" \
    -u "$USER:$PASSWORD" \
    -H "Content-Type: application/json" \
    -d "$CLUBS_PAYLOAD" | jq .

# 4. Synchronize Players
echo -e "\n4. Synchronizing Players..."
PLAYERS=$(echo "$TOURNEY_JSON" | jq -c '.players')
PLAYERS_PAYLOAD=$(jq -n --argjson v "$WP_VERSION" --argjson p "$PLAYERS" '{version: ($v | tonumber), players: $p}')

curl -s -X POST "${WP_URL}/players-sync?postId=$PAGE_ID" \
    -u "$USER:$PASSWORD" \
    -H "Content-Type: application/json" \
    -d "$PLAYERS_PAYLOAD" | jq .

# 5. Synchronize Competitions
echo -e "\n5. Synchronizing Competitions..."
# Competitions need to be wrapped in "events" array for the API
COMPS=$(echo "$TOURNEY_JSON" | jq -c '.competitions | map(select(. != null))')
COMPS_PAYLOAD=$(jq -n --argjson e "$COMPS" '{events: $e}')

curl -s -X POST "${WP_URL}/competitions-sync?postId=$PAGE_ID" \
    -u "$USER:$PASSWORD" \
    -H "Content-Type: application/json" \
    -d "$COMPS_PAYLOAD" | jq .

echo -e "\nImport finished successfully!"
echo "View your tournament at: https://localhost/$FULL_SLUG"
