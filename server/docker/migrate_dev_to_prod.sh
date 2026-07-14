#!/bin/bash
set -euo pipefail

DEV_DIR="dev"
PROD_DIR="prod_rolicht"

# We must run this from the server/docker/ directory
cd "$(dirname "$0")"

echo "🧹 Staging dev database dump..."
if ! docker ps --format '{{.Names}}' | grep -q 'wp-cli-instance'; then
    echo "❌ Fehler: wp-cli-instance Container läuft nicht! Bitte starte die dev-Umgebung zuerst."
    exit 1
fi

# Dump dev database
docker exec wp-cli-instance wp db export /var/www/html/dev_dump.sql --allow-root

echo "🔄 Synchronisiere WordPress-Dateien (ohne wp-config.php)..."
mkdir -p "$PROD_DIR/wp_data"
rsync -av --exclude="wp-config.php" "$DEV_DIR/wp_data/" "$PROD_DIR/wp_data/"

echo "✅ WordPress-Dateien und Datenbank-Dump erfolgreich nach $PROD_DIR/wp_data übertragen."
echo ""
echo "Nächste Schritte:"
echo "1. Lade das Verzeichnis 'server/docker/$PROD_DIR' auf deinen Server hoch."
echo "2. Starte die Container auf dem Server."
echo "3. Führe auf dem Server im Verzeichnis '$PROD_DIR' folgenden Befehl aus, um die Datenbank zu importieren und die URLs anzupassen:"
echo "   ./import_dev_db.sh"
