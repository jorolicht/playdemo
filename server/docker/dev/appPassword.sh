#!/bin/bash

# Pfad zur Environment-Datei
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"

# Extracting the password 
APP_PASSWORD=$(docker exec wp-cli-instance wp user application-password create robert myapp --allow-root | awk '/Password: /{print $2}') 

echo "App Password: $APP_PASSWORD"
