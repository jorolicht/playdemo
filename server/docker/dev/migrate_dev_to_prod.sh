#!/bin/bash
set -euo pipefail

# Path setup
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEV_DIR="$SCRIPT_DIR"
BASE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ZIP_OUTPUT="$BASE_DIR/dev_migration.zip"

echo "🧹 Erstelle Entwicklungs-Datenbank-Dump..."
if ! docker ps --format '{{.Names}}' | grep -q 'wp-cli-instance'; then
    echo "❌ Fehler: wp-cli-instance Container läuft nicht! Bitte starte die dev-Umgebung zuerst."
    exit 1
fi

# Export DB into container wp_data
docker exec wp-cli-instance wp db export /var/www/html/dev_dump.sql --allow-root

echo "📦 Erstelle Zip-Archiv dev_migration.zip (ohne wp-config.php)..."
rm -f "$ZIP_OUTPUT"

if [ -d "$DEV_DIR/wp_data" ]; then
    (cd "$DEV_DIR/wp_data" && zip -r "$ZIP_OUTPUT" . -x "wp-config.php")
else
    echo "❌ Fehler: Verzeichnis $DEV_DIR/wp_data nicht gefunden!"
    exit 1
fi

# Remove temporary sql dump from local dev/wp_data
rm -f "$DEV_DIR/wp_data/dev_dump.sql"

echo "✅ Migration-Zip erfolgreich erstellt: $ZIP_OUTPUT"
echo ""
echo "Nächste Schritte:"
echo "1. Kopiere '$(basename "$ZIP_OUTPUT")' auf dein Produktivsystem in dein Produktions-Verzeichnis (wo docker-compose.yml liegt)."
echo "2. Führe auf dem Produktivsystem in diesem Verzeichnis folgenden Befehl aus:"
echo "   ./import_to_prod_from_dev.sh"
