#!/bin/bash

# Configuration
USER="robert"
PASSWORD="mx5dkwkidnTmNavhDnSeVmqK"
BASE_URL="http://localhost:8080/wp-json/tourney/v1"
POST_ID=11 # Replace with a valid post ID if needed

echo "Testing Clubs API with Global Versioning..."

# 1. Get Clubs (initial state)
echo -e "\n1. GET /clubs"
INIT_RES=$(curl -s -X GET "${BASE_URL}/clubs?postId=${POST_ID}")
echo $INIT_RES | jq .
VERSION=$(echo $INIT_RES | jq .version)

# 2. Sync Clubs (Add two clubs)
echo -e "\n2. POST /clubs-sync (Add two clubs, current version: $VERSION)"
SYNC_DATA='{
  "version": '"$VERSION"',
  "clubs": [
    {
      "id": 1,
      "name": "TTC Test",
      "normalizedName": "ttc test",
      "active": true
    },
    {
      "id": 2,
      "name": "SV Beispiel",
      "normalizedName": "sv beispiel",
      "active": true
    }
  ]
}'

RES=$(curl -s -X POST "${BASE_URL}/clubs-sync?postId=${POST_ID}" \
     -u "$USER:$PASSWORD" \
     -H "Content-Type: application/json" \
     -d "$SYNC_DATA")
echo $RES | jq .
VERSION=$(echo $RES | jq .version)

# 3. Get Clubs again
echo -e "\n3. GET /clubs (after sync, version should be $VERSION)"
curl -s -X GET "${BASE_URL}/clubs?postId=${POST_ID}" | jq .

# 4. Sync Clubs (Update club 1, Soft Delete club 2)
echo -e "\n4. POST /clubs-sync (Update and Soft Delete, current version: $VERSION)"
SYNC_DATA_2='{
  "version": '"$VERSION"',
  "clubs": [
    {
      "id": 1,
      "name": "TTC Test Updated",
      "normalizedName": "ttc test updated",
      "active": true
    },
    {
      "id": 2,
      "name": "SV Beispiel",
      "normalizedName": "sv beispiel",
      "active": false
    }
  ]
}'

RES=$(curl -s -X POST "${BASE_URL}/clubs-sync?postId=${POST_ID}" \
     -u "$USER:$PASSWORD" \
     -H "Content-Type: application/json" \
     -d "$SYNC_DATA_2")
echo $RES | jq .
VERSION=$(echo $RES | jq .version)

# 5. Get Clubs final
echo -e "\n5. GET /clubs (final, version should be $VERSION)"
curl -s -X GET "${BASE_URL}/clubs?postId=${POST_ID}" | jq .
