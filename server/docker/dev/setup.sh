#!/bin/bash

# Pfad zur Environment-Datei
ENV_FILE="../../env/dev.env"
COMPOSE_FILE="docker-compose.yml"

# Liste der zu installierenden Plugins
PLUGINS="wp-members hcaptcha-for-forms-and-more wp-mail-smtp loco-translate wp-webauthn"

set -euo pipefail
set -a                       # Schaltet "Auto-Export" ein
source $ENV_FILE             # Liest die Datei ein und exportiert jede Zeile automatisch
set +a                       # Schaltet "Auto-Export" wieder aus

# WPCLI="docker compose exec -T wp-cli wp --path=/var/www/html --allow-root"
# WPCLI_PHP="docker compose exec -T wp-cli php -d memory_limit=-1 /usr/local/bin/wp --path=/var/www/html --allow-root"

# Hilfsfunktionen für WP-CLI
WPCLI="docker compose exec -T wp-cli wp --path=/var/www/html --allow-root"
wp_cmd() {
    docker exec -u www-data wp-cli-instance wp "$@"
}

# 1. Docker Compose hochfahren
echo "🐳 Starte Docker Container..."
mkdir -p logs
chmod -R 777 logs
# --wait sorgt dafür, dass das Skript wartet, bis die Container "healthy" sind
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d --wait

# 2. Warten, bis die Datenbank wirklich bereit ist (Sicherheitscheck)
# echo "⏳ Warte auf MariaDB (TCP-Check auf Port 3306)..."
# MAX_RETRIES=30
# COUNT=0

# # Wir testen direkt, ob der Port im DB-Container offen ist
# until docker exec wp-db-instance sh -c 'netstat -tln | grep -q 3306' || [ $COUNT -eq $MAX_RETRIES ]; do
#     echo "   ... Datenbank bootet noch ($COUNT/$MAX_RETRIES)"
#     sleep 3
#     ((COUNT++))
# done

echo "⏳ Prüfe MariaDB Erreichbarkeit (Login-Check)..."
MAX_RETRIES=30
COUNT=0

# Wir versuchen uns einzuloggen. Erst wenn das klappt, geht es weiter.
until docker exec wp-db-instance mariadb-admin ping -u"root" -p"$DB_ROOT_PASSWORD" --silent || [ $COUNT -eq $MAX_RETRIES ]; do
    echo "   ... MariaDB ist noch nicht bereit (Versuch $COUNT/$MAX_RETRIES)"
    sleep 3
    ((COUNT++))
done


# Zusätzlicher Sicherheits-Sleep, damit MariaDB die Grant-Tables laden kann
sleep 5


if [ $COUNT -eq $MAX_RETRIES ]; then
    echo "❌ Fehler: Datenbank konnte nicht erreicht werden."
    exit 1
fi


# 3. WordPress Installation
if ! wp_cmd core is-installed --quiet; then
    echo "📥 Installiere WordPress..."
    wp_cmd core install \
        --url="http://localhost:8080" \
        --title="${WP_TITLE}" \
        --admin_user="${WP_ADMIN_USER}" \
        --admin_password="${WP_ADMIN_PASSWORD}" \
        --admin_email="${WP_ADMIN_EMAIL}" \
        --skip-email
    
    # Sprache und Permalinks
    echo "🌐 Installiere Sprache de_DE..."
    wp_cmd language core install de_DE --activate
    
    echo "🔗 Konfiguriere Permalinks..."
    wp_cmd rewrite structure '/%postname%/'
    wp_cmd rewrite flush --hard
    
    echo "🧹 Entferne Standard-Plugins (akismet, hello)..."
    wp_cmd plugin delete akismet hello --quiet

    echo "🔌 Installiere neue Plugins (hCaptcha, WP-Members)..."
    wp_cmd plugin install ${PLUGINS} --activate

    $WPCLI option update wp_mail_smtp_mailer other
    $WPCLI option update wp_mail_smtp '{
    "mail": {
        "mailer": "smtp",
        "from_email": "'"$MAIL_FROM"'",
        "from_name": "'"$MAIL_FROM_NAME"'",
        "from_email_force": true,
        "from_name_force": true
    },
    "smtp": {
        "host": "'"$MAIL_HOST"'",
        "port": "'"$MAIL_PORT"'",
        "encryption": "'"$MAIL_ENCRYPTION"'",
        "auth": true,
        "user": "'"$MAIL_USER"'",
        "pass": "'"$MAIL_PASS"'"
    }
    }' --format=json

    echo "Testmail an robert.lichtenegger@icloud.com senden ..."
    docker compose exec wp-cli wp eval "
    wp_mail(
    'robert.lichtenegger@icloud.com',
    'SMTP Test',
    'Testmail aus WP-CLI (Docker)'
    );" --allow-root

    echo "✅ WordPress erfolgreich installiert und konfiguriert."
else
    echo "✅ WordPress bereits konfiguriert."
fi

echo "📦 Prüfe JWT Library (Composer)..."

PLUGIN_PATH="wp-content/plugins/tourney"
VENDOR_PATH="wp_data/${PLUGIN_PATH}/vendor"

if [ ! -f "${VENDOR_PATH}/autoload.php" ]; then
    echo "➡️ Installiere firebase/php-jwt..."

    docker compose run --rm composer \
        require firebase/php-jwt \
        --working-dir=${PLUGIN_PATH}

    echo "✅ JWT Library installiert."
else
    echo "✅ JWT Library bereits vorhanden – überspringe Installation."
fi

echo "-------------------------------------------------------------------------------------"
echo "Fertig! Deine Seite ist bereit: ${WP_URL}  | Play: http://localhost:${PLAY_HTTP_PORT}"
echo "-------------------------------------------------------------------------------------"
