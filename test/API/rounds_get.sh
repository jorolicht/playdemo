#!/bin/bash
# Get rounds from WordPress

POST_ID=${1:-1}
DOMAIN=${2:-"localhost:8080"}

curl -X GET "http://${DOMAIN}/wp-json/tourney/v1/rounds?postId=${POST_ID}"
