#!/bin/bash

# Configuration
USER="robert"
PASSWORD="mx5dkwkidnTmNavhDnSeVmqK"
BASE_URL="http://localhost:8080/wp-json/tourney/v1"
POST_ID=11 # Replace with a valid post ID if needed

echo "Testing Players API..."

# 1. Get Players (empty initially or existing)
echo -e "\n1. GET /players"
curl -s -X GET "${BASE_URL}/players?postId=${POST_ID}" | jq .
