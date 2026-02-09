#!/bin/bash

# Pfad zur Environment-Datei
ENV_FILE="../env/docker.env"
COMPOSE_FILE="docker-compose.yml"

# Extracting the password 
APP_PASSWORD=$(docker exec wp-cli-instance wp  user application-password create robert myapp --allow-root | awk '/Password: /{print $2}') 

echo "App Password: $APP_PASSWORD"
