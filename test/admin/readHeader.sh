#!/bin/bash

# Configuration
USER="robert"
PASSWORD="BwMkcFExPq1HMysCZKP8jJ11"
BASE_URL="http://localhost:8080/wp-json/tourney/v1"
# URL der WP REST API
API_URL="http://localhost:8080/wp-json/wp/v2/template-parts?slug=header"

# Curl-Anfrage senden und JSON-Antwort verarbeiten
curl "$API_URL" \
  -H "Content-Type: application/json" \
  -u "$USER:$PASSWORD"  \
  | jq '.[] | {title, content}'
