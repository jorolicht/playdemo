#!/bin/bash
# Sync rounds with WordPress

POST_ID=${1:-1} # Default post ID is 1
DOMAIN=${2:-"localhost:8080"}

# Example round data (JSON encoded)
ROUND_JSON='{
  "id": 1,
  "coId": 1,
  "name": "Test Round 1",
  "rndCfg": "VRGR",
  "status": "CFG",
  "demo": false,
  "size": 8,
  "noPlayers": 8,
  "noWinSets": 3,
  "prefId": null,
  "nextIds": [],
  "quali": "ALL",
  "deleted": false,
  "version": 1
}'

curl -X POST "http://${DOMAIN}/wp-json/tourney/v1/rounds-sync?postId=${POST_ID}" \
  -H "Content-Type: application/json" \
  -d "{
    \"events\": [${ROUND_JSON}]
  }"
