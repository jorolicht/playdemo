#!/bin/bash

# Configuration
USER="robert"
PASSWORD="mx5dkwkidnTmNavhDnSeVmqK"
BASE_URL="http://localhost:8080/wp-json/tourney/v1"
POST_ID=11 # Replace with a valid post ID if needed

echo "Testing Tourney API with Global Versioning..."

# 1. Get Tourney (initial state)
echo -e "\n1. GET /read"
INIT_RES=$(curl -s -X GET "${BASE_URL}/read?postId=${POST_ID}")
echo $INIT_RES | jq .
VERSION=$(echo $INIT_RES | jq .version)

# 2. Sync Tourney (Add/Update tourney data)
echo -e "\n2. POST /tourney-sync (current version: $VERSION)"
SYNC_DATA=$(jq -n --arg v "$VERSION" '{
  "version": ($v | tonumber),
  "tourney": {
    "name": "Sommer-Turnier 2026",
    "organizer": "TTC Testhausen",
    "startDate": 20260701,
    "endDate": 20260702,
    "ident": "ST2026",
    "typ": "TableTennis",
    "contact": {
        "lastname": "Tester",
        "firstname": "Tom",
        "phone": "0987-654321",
        "email": "tom@test.com"
    },
    "address": {
        "description": "Sporthalle",
        "country": "DE",
        "zip": "54321",
        "city": "Testhausen",
        "street": "Teststr. 12"
    },
    "version": ($v | tonumber)
  }
}')

RES=$(curl -s -X POST "${BASE_URL}/tourney-sync?postId=${POST_ID}" \
     -u "$USER:$PASSWORD" \
     -H "Content-Type: application/json" \
     -d "$SYNC_DATA")
echo $RES | jq .
VERSION=$(echo $RES | jq .version)

# 3. Get Tourney again
echo -e "\n3. GET /read (after sync, version should be $VERSION)"
curl -s -X GET "${BASE_URL}/read?postId=${POST_ID}" | jq .
