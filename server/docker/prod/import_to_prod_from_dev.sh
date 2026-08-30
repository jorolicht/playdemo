#!/bin/bash
set -euo pipefail

# Pfad bestimmen
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE="$SCRIPT_DIR/.env"
if [ -f "$ENV_FILE" ]; then
    set -a
    source "$ENV_FILE"
    set +a
else
    echo "❌ Fehler: .env Datei in $SCRIPT_DIR nicht gefunden!"
    exit 1
fi

ZIP_FILE="${1:-dev_migration.zip}"
if [ ! -f "$ZIP_FILE" ] && [ -f "../dev_migration.zip" ]; then
    ZIP_FILE="../dev_migration.zip"
fi

if [ -f "$ZIP_FILE" ]; then
    echo "📦 Entpacke Migration-Archiv $ZIP_FILE (wp-config.php wird ausgeschlossen)..."
    mkdir -p wp_data
    
    SUDO_CMD=""
    if [ "$(id -u)" -ne 0 ] && [ -d "wp_data" ] && { [ ! -w "wp_data" ] || [ -f "wp_data/wp-cron.php" -a ! -w "wp_data/wp-cron.php" ]; }; then
        echo "🔒 Anpassung der Schreibrechte via sudo..."
        SUDO_CMD="sudo"
    fi
    
    $SUDO_CMD unzip -o "$ZIP_FILE" -x "wp-config.php" -d wp_data/
elif [ ! -f "wp_data/dev_dump.sql" ]; then
    echo "❌ Fehler: Weder $ZIP_FILE noch wp_data/dev_dump.sql gefunden!"
    exit 1
fi

echo "🔍 Prüfe, ob Container wp-cli-instance läuft..."
if ! docker ps --format '{{.Names}}' | grep -q 'wp-cli-instance'; then
    echo "❌ Fehler: wp-cli-instance Container läuft nicht! Bitte starte die prod-Umgebung zuerst."
    exit 1
fi

echo "📥 Importiere Entwicklungs-Datenbank..."
docker exec wp-cli-instance wp db import /var/www/html/dev_dump.sql --allow-root

echo "🔗 Passe URLs in der Datenbank an..."
docker exec wp-cli-instance wp search-replace "http://localhost:8080" "${WP_URL}" --allow-root
docker exec wp-cli-instance wp search-replace "https://localhost" "${WP_URL}" --allow-root
docker exec wp-cli-instance wp search-replace "http://localhost" "${WP_URL}" --allow-root

echo "🧹 Leere WordPress-Cache..."
docker exec wp-cli-instance wp cache flush --allow-root

echo "🗑️ Entferne temporären Datenbank-Dump..."
${SUDO_CMD:-} rm -f wp_data/dev_dump.sql

echo "✅ Import und Migration auf Produktivsystem erfolgreich abgeschlossen."
