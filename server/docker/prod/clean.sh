#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Pfad zur Environment-Datei bestimmen
ENV_FILE="$SCRIPT_DIR/.env"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"

set -euo pipefail
set -a                       # Schaltet "Auto-Export" ein
source "$ENV_FILE"           # Liest die Datei ein und exportiert jede Zeile automatisch
set +a                       # Schaltet "Auto-Export" wieder aus

docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" down -v --remove-orphans

sudo rm -rf wp_data/* db_data/* db_init/* logs/*

# Stellt sicher, dass auch versteckte Dateien (z. B. .htaccess) gelöscht werden
sudo find wp_data db_data db_init logs -mindepth 1 -delete

echo "✅ Docker gestoppt und Daten gelöscht"
