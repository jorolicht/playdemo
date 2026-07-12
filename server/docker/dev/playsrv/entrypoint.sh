#!/bin/bash
set -e

echo "Starte Server in Mode: ${APP_ENVIRONMENT:-development}"
echo "Logfile path: ${PLAY_LOGFILE_PATH}"
echo "Pidfile path: ${PLAY_PIDFILE_PATH}"

# PIDFILE nur anlegen, wenn nicht /dev/null
if [ "${PLAY_PIDFILE_PATH:-/dev/null}" != "/dev/null" ]; then
    echo "Using PID file: $PLAY_PIDFILE_PATH"
    mkdir -p "$(dirname "$PLAY_PIDFILE_PATH")"
    : > "$PLAY_PIDFILE_PATH"
else
    echo "PID file disabled (PLAY_PIDFILE_PATH=/dev/null)"
fi

# Pfad zu den Dateien (WORKDIR ist im Container gesetzt)
CONF_DIR="./conf"

if [ "$APP_ENVIRONMENT" = "production" ]; then
    echo "Set Environment to Production-Configuration..."
    cp "$CONF_DIR/routes.prod" "$CONF_DIR/routes" || echo "File routes.prod not found"
    cp "$CONF_DIR/logback.prod.xml" "$CONF_DIR/logback.xml" || echo "File logback.prod.xml not found"
else
    echo "Use Development-Configuration (Standard)"
    # Optional: Falls du im Dev-Mode spezielle Dateien zurücksetzen willst
fi

# Übergib die Kontrolle an das eigentliche Programm (ENTRYPOINT von Docker)
# $@ sorgt dafür, dass Parameter von 'docker run' oder 'CMD' durchgereicht werden
exec "$@"