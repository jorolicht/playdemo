#!/bin/bash
. "$(dirname "$0")/../credentials.env"   # TEST_WP_USER, TEST_WP_PASSWORD (Vorlage: test/credentials.env.example)

# Configuration
USER="$TEST_WP_USER"
PASSWORD="$TEST_WP_PASSWORD"
BASE_URL="http://localhost:8080/wp-json/tourney/v1"
POST_ID=11 # Replace with a valid post ID if needed

echo "Testing Players API..."

# 1. Get Players (empty initially or existing)
echo -e "\n1. GET /players"
curl -s -X GET "${BASE_URL}/players?postId=${POST_ID}" | jq .
