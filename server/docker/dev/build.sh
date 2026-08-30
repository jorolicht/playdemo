#!/bin/bash

# Pfad zur Environment-Datei
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"

set -euo pipefail
set -a                       # Schaltet "Auto-Export" ein
source "$ENV_FILE"           # Liest die Datei ein und exportiert jede Zeile automatisch
set +a                       # Schaltet "Auto-Export" wieder aus

mkdir -p "$SCRIPT_DIR/logs"
chmod -R 777 "$SCRIPT_DIR/logs"
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d --build

echo "✅ Docker Images neu erstellt, Container gestartet."