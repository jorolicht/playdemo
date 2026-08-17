#!/bin/bash

# Configuration
BASE_URL="http://localhost:8080"
USER="robert"
PASS="x0CsysbewmNvehrtOEnCgsuT"

if [ -z "$1" ]; then
    echo "Usage: $0 <tournament-slug>"
    echo "Example: $0 20281201-winter-turnier-2028"
    exit 1
fi

SLUG="$1"

echo "Resolving Slug: $SLUG ..."

# 1. Resolve Slug to Post ID via WordPress Core API
# We look for the CPT 'tourney'. Note: WP returns an array.
POST_DATA=$(curl -s -u "$USER:$PASS" "$BASE_URL/wp-json/wp/v2/tourney?slug=$SLUG&status=any")
PAGE_ID=$(echo "$POST_DATA" | jq -r '.[0].id // empty')

if [ -z "$PAGE_ID" ] || [ "$PAGE_ID" == "null" ]; then
    echo "Error: Tournament with slug '$SLUG' not found."
    exit 1
fi

echo "Found Tournament ID: $PAGE_ID"
echo "--------------------------------------------------"

# 2. Fetch all Tournament related data from custom endpoints
echo "Fetching Details..."

# Basic Tourney Data
BASIC=$(curl -s -u "$USER:$PASS" "$BASE_URL/wp-json/tourney/v1/read?postId=$PAGE_ID")
# Clubs
CLUBS=$(curl -s -u "$USER:$PASS" "$BASE_URL/wp-json/tourney/v1/clubs?postId=$PAGE_ID")
# Players
PLAYERS=$(curl -s -u "$USER:$PASS" "$BASE_URL/wp-json/tourney/v1/players?postId=$PAGE_ID")
# Competitions
COMPS=$(curl -s -u "$USER:$PASS" "$BASE_URL/wp-json/tourney/v1/competitions?postId=$PAGE_ID")
# Rounds
ROUNDS=$(curl -s -u "$USER:$PASS" "$BASE_URL/wp-json/tourney/v1/rounds?postId=$PAGE_ID")

# 3. Consolidate and Output
jq -n   --argjson basic "$BASIC"   --argjson clubs "$CLUBS"   --argjson players "$PLAYERS"   --argjson comps "$COMPS"   --argjson rounds "$ROUNDS"   '{
    tournament: $basic.tourney,
    version: $basic.version,
    data: {
      clubs: $clubs.clubs,
      players: $players.players,
      competitions: $comps.competitions,
      rounds: $rounds.rounds
    }
  }'
