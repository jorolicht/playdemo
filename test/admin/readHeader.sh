#!/bin/bash
. "$(dirname "$0")/../credentials.env"   # TEST_WP_USER, TEST_WP_PASSWORD (Vorlage: test/credentials.env.example)

# Configuration
USER="$TEST_WP_USER"
PASSWORD="$TEST_WP_PASSWORD"
BASE_URL="http://localhost:8080/wp-json/tourney/v1"
# URL der WP REST API
API_URL="http://localhost:8080/wp-json/wp/v2/template-parts?slug=header"

# Curl-Anfrage senden und JSON-Antwort verarbeiten
curl "$API_URL" \
  -H "Content-Type: application/json" \
  -u "$USER:$PASSWORD"  \
  | jq '.[] | {title, content}'
