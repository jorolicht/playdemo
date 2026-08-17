#!/bin/bash

# Configuration
USER="robert"
PASSWORD="mx5dkwkidnTmNavhDnSeVmqK"
BASE_URL="http://localhost:8080/wp-json/tourney/v1"
POST_ID=11 # Replace with a valid post ID if needed

echo "Testing Players API with Global Versioning..."

# 1. Get Players (initial state)
echo -e "\n1. GET /players"
INIT_RES=$(curl -s -X GET "${BASE_URL}/players?postId=${POST_ID}")
echo $INIT_RES | jq .
VERSION=$(echo $INIT_RES | jq .version)

# 2. Sync Players (Add two players)
echo -e "\n2. POST /players-sync (Add two players, current version: $VERSION)"
SYNC_DATA='{
  "version": '"$VERSION"',
  "players": [
    {
      "id": 1,
      "firstName": "John",
      "lastName": "Doe",
      "clubId": 1,
      "birthYear": 1990,
      "active": true
    },
    {
      "id": 2,
      "firstName": "Jane",
      "lastName": "Smith",
      "clubId": 1,
      "birthYear": 1992,
      "active": true
    }
  ]
}'

RES=$(curl -s -X POST "${BASE_URL}/players-sync?postId=${POST_ID}" \
     -u "$USER:$PASSWORD" \
     -H "Content-Type: application/json" \
     -d "$SYNC_DATA")
echo $RES | jq .
VERSION=$(echo $RES | jq .version)

# 3. Get Players again
echo -e "\n3. GET /players (after sync, version should be $VERSION)"
curl -s -X GET "${BASE_URL}/players?postId=${POST_ID}" | jq .

# 4. Sync Players (Update player 1, Soft Delete player 2)
echo -e "\n4. POST /players-sync (Update and Soft Delete, current version: $VERSION)"
SYNC_DATA_2='{
  "version": '"$VERSION"',
  "players": [
    {
      "id": 1,
      "firstName": "John",
      "lastName": "Doe Updated",
      "clubId": 1,
      "birthYear": 1990,
      "active": true
    },
    {
      "id": 2,
      "firstName": "Jane",
      "lastName": "Smith",
      "clubId": 1,
      "birthYear": 1992,
      "active": false
    }
  ]
}'

RES=$(curl -s -X POST "${BASE_URL}/players-sync?postId=${POST_ID}" \
     -u "$USER:$PASSWORD" \
     -H "Content-Type: application/json" \
     -d "$SYNC_DATA_2")
echo $RES | jq .
VERSION=$(echo $RES | jq .version)

# 5. Get Players final
echo -e "\n5. GET /players (final, version should be $VERSION)"
curl -s -X GET "${BASE_URL}/players?postId=${POST_ID}" | jq .
