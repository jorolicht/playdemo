#!/bin/bash

# Configuration
USER="robert"
PASSWORD="mx5dkwkidnTmNavhDnSeVmqK"
BASE_URL="http://localhost:8080"

echo "Reading WP Template Part: header (tt5)..."

# Note: We fetch template parts. We might need to filter by slug or theme.
# Standard endpoint: /wp/v2/template-parts
# To find the one from a specific theme, we usually look at the 'theme' field in the response.

curl -s -u "$USER:$PASSWORD" \
     -X GET "$BASE_URL/wp-json/wp/v2/template-parts?slug=header" | jq .
