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

mkdir -p "$SCRIPT_DIR/logs"
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d 

echo "✅ Docker Container gestartet."