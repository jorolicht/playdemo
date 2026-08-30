#!/bin/bash

# Pfad zur Environment-Datei
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"

set -euo pipefail
set -a                       # Schaltet "Auto-Export" ein
source "$ENV_FILE"           # Liest die Datei ein und exportiert jede Zeile automatisch
set +a                       # Schaltet "Auto-Export" wieder aus

docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" down

echo "✅ Docker Container sind gestoppt."