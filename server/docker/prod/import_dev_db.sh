#!/bin/bash
set -euo pipefail

# Pfad zur Environment-Datei
ENV_FILE=".env"

set -a
source $ENV_FILE
set +a

echo "📥 Importiere Entwicklungs-Datenbank..."
if [ ! -f wp_data/dev_dump.sql ]; then
    echo "❌ Fehler: wp_data/dev_dump.sql nicht gefunden!"
    exit 1
fi

# Import DB
docker exec wp-cli-instance wp db import /var/www/html/dev_dump.sql --allow-root

# Search and Replace URLs
echo "🔗 Passe URLs in der Datenbank an..."
# Falls dev-Installation http://localhost:8080 war
docker exec wp-cli-instance wp search-replace "http://localhost:8080" "${WP_URL}" --allow-root
# Falls dev-Installation https://localhost war
docker exec wp-cli-instance wp search-replace "https://localhost" "${WP_URL}" --allow-root

# Flush Cache
docker exec wp-cli-instance wp cache flush --allow-root

# Aufräumen
rm -f wp_data/dev_dump.sql

echo "✅ Datenbank-Import und URL-Anpassung abgeschlossen."
