#!/bin/bash

# Pfad zur Environment-Datei
ENV_FILE="../../env/dev.env"
COMPOSE_FILE="docker-compose.yml"

set -euo pipefail
set -a                       # Schaltet "Auto-Export" ein
source $ENV_FILE             # Liest die Datei ein und exportiert jede Zeile automatisch
set +a                       # Schaltet "Auto-Export" wieder aus

mkdir -p logs
chmod -R 777 logs
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d 

echo "✅ Docker Container sind gestartet."