#!/bin/bash


mkdir -p wp_data
mkdir -p db_data
mkdir -p db_init
mkdir -p logs
sudo chown -R 33:33 wp_data
sudo chown -R 33:33 db_init
sudo chown -R 999:999 db_data
sudo chmod -R 775 wp_data
sudo chmod -R 700 db_data

echo "✅ Verzeichnisse erstellt und Berechtigungen gesetzt."

# Pfad zur Environment-Datei
ENV_FILE=".env"
COMPOSE_FILE="docker-compose.yml"

set -euo pipefail
set -a                       # Schaltet "Auto-Export" ein
source $ENV_FILE             # Liest die Datei ein und exportiert jede Zeile automatisch
set +a                       # Schaltet "Auto-Export" wieder aus

docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d --build

echo "✅ Docker Images neu erstellt, Container gestartet."