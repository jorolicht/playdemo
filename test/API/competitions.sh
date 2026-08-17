#!/bin/bash

# Configuration
USER="robert"
PASSWORD="mx5dkwkidnTmNavhDnSeVmqK"
BASE_URL="http://localhost:8080/wp-json/tourney/v1"
POST_ID=11 # Replace with a valid post ID if needed

echo "Testing Competitions API with Versioning and Participants..."

# 1. Get Competitions (initial state)
echo -e "\n1. GET /competitions"
curl -s -X GET "${BASE_URL}/competitions?postId=${POST_ID}" | jq .

# 2. Sync Competitions (Add competition with participants)
echo -e "\n2. POST /competitions-sync (Add competition with version 1 and pants)"
SYNC_DATA='{
  "events": [
    {
      "id": 1,
      "name": "Herren A",
      "typ": 1,
      "startDate": "2026-06-01",
      "status": 0,
      "activ": true,
      "deleted": false,
      "version": 1,
      "pants": [
        {
          "id": "P1",
          "name": "Max Mustermann",
          "club": "TTC Test",
          "status": "REDY"
        },
        {
          "id": "P2",
          "name": "Erika Musterfrau",
          "club": "TTV Beispiel",
          "status": "REDY"
        }
      ]
    }
  ]
}'

curl -s -X POST "${BASE_URL}/competitions-sync?postId=${POST_ID}" \
     -u "$USER:$PASSWORD" \
     -H "Content-Type: application/json" \
     -d "$SYNC_DATA" | jq .

# 3. Get Competitions after add
echo -e "\n3. GET /competitions (after add)"
curl -s -X GET "${BASE_URL}/competitions?postId=${POST_ID}" | jq .

# 4. Sync Competitions (Update: add another participant and increment version)
echo -e "\n4. POST /competitions-sync (Update: add participant, version 2)"
SYNC_DATA_UPDATE='{
  "events": [
    {
      "id": 1,
      "name": "Herren A",
      "typ": 1,
      "startDate": "2026-06-01",
      "status": 0,
      "activ": true,
      "deleted": false,
      "version": 2,
      "pants": [
        {
          "id": "P1",
          "name": "Max Mustermann",
          "club": "TTC Test",
          "status": "REDY"
        },
        {
          "id": "P2",
          "name": "Erika Musterfrau",
          "club": "TTV Beispiel",
          "status": "REDY"
        },
        {
          "id": "P3",
          "name": "Hans Dampf",
          "club": "SV Demo",
          "status": "REDY"
        }
      ]
    }
  ]
}'

curl -s -X POST "${BASE_URL}/competitions-sync?postId=${POST_ID}" \
     -u "$USER:$PASSWORD" \
     -H "Content-Type: application/json" \
     -d "$SYNC_DATA_UPDATE" | jq .

# 5. Get Competitions final
echo -e "\n5. GET /competitions (final)"
curl -s -X GET "${BASE_URL}/competitions?postId=${POST_ID}" | jq .
