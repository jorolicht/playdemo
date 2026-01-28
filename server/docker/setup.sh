#!/bin/bash

# Pfad zur Environment-Datei
ENV_FILE="../env/docker.env"
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


echo "-------------------------------------------------------------------------------------"
echo "Fertig! Deine Seite ist bereit: ${WP_URL}  | Play: http://localhost:${PLAY_HTTP_PORT}"
echo "-------------------------------------------------------------------------------------"


# # 3. Der robuste Check: Warten bis die DB wirklich 'Ready' ist
# echo "Warte auf Datenbank-Bereitschaft (das kann 10-20 Sek. dauern)..."

# # Wir versuchen alle 2 Sekunden eine Verbindung aufzubauen
# # 'mysqladmin ping' ist der zuverlässigste Weg zu prüfen, ob MySQL Befehle annimmt
# MAX_TRIES=30
# COUNT=0

# while ! docker compose exec db mysqladmin ping -h"localhost" -u"${DB_USER}" -p"${DB_PASSWORD}" --silent; do
#     COUNT=$((COUNT+1))
#     if [ $COUNT -ge $MAX_TRIES ]; then
#         echo "Fehler: Datenbank wurde nicht rechtzeitig bereit. Abbruch."
#         exit 1
#     fi
#     echo "Datenbank lädt noch... (Versuch $COUNT/$MAX_TRIES)"
#     sleep 2
# done
# echo "Datenbank ist bereit! Starte WordPress-Konfiguration..."

# # Server datenbank erstellen, falls nicht vorhanden
# echo "Server datenbank erstellen, falls nicht vorhanden...."
# docker compose exec db mariadb -uroot -p${DB_ROOT_PASSWORD} -e \
# "CREATE DATABASE IF NOT EXISTS ${DB_NAME_SRV} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"


# # 4. WordPress herunterladen (falls noch nicht geschehen)
# echo "Lade wordpress herunter mit deutschen locale..."
# $WPCLI_PHP core download --locale=de_DE --force

# Das Wort wp explizit vor den Befehl setzen, damit das Image weiß, welches Programm die Argumente verarbeiten soll.
# Falsch: docker compose run --rm wp-cli core download
# Richtig: docker compose run --rm wp-cli **wp** core download

# # 5. Konfiguration erstellen
# echo "Konfiguration erstellen..."
# $WPCLI_PHP config create --dbname="${DB_NAME}" \
#    --dbuser="${DB_USER}" --dbpass="${DB_PASSWORD}" \
#    --dbhost="db" --force \
#    --extra-php <<PHP
# define('WP_DEBUG', false);
# define('WP_DEBUG_LOG', true);
# define('WP_MEMORY_LIMIT', '256M');
# define('WP_MAX_MEMORY_LIMIT', '256M');
# define( 'WP_HOME', 'https://${MY_DOMAIN}' );
# define( 'WP_SITEURL', 'https://${MY_DOMAIN}' );
# define( 'FORCE_SSL_ADMIN', true );
# if (isset(\$_SERVER['HTTP_X_FORWARDED_PROTO']) && \$_SERVER['HTTP_X_FORWARDED_PROTO'] === 'https') {
#     \$_SERVER['HTTPS'] = 'on';
# }
# PHP

# # 6. Installation ausführen
# echo "Führe Installation aus..."
# $WPCLI_PHP core install --url="${WP_URL}" --title="${WP_TITLE}" --admin_user="${WP_ADMIN_USER}"  --admin_password="${WP_ADMIN_PASSWORD}" --admin_email="${WP_ADMIN_EMAIL}"
 
# # 7. Wordpress service starten 
# echo "Starte Wordpress Service..."
# docker compose up -d wordpress
# sleep 5

# # 7. Optionale Konfigurationen
# echo "Konfiguriere Structure ..."
# $WPCLI rewrite structure '/%postname%/' --hard
# $WPCLI rewrite flush --hard # Regeln neu generieren

# # 8. Install E-Mail Plugin und konfiguriere SMTP
# echo "Install E-Mail Plugin und konfiguriere SMTP ..."
# $WPCLI plugin install wp-mail-smtp --activate
# $WPCLI option update wp_mail_smtp_mailer other
# $WPCLI option update wp_mail_smtp '{
#   "mail": {
#     "mailer": "smtp",
#     "from_email": "'"$MAIL_FROM"'",
#     "from_name": "'"$MAIL_FROM_NAME"'",
#     "from_email_force": true,
#     "from_name_force": true
#   },
#   "smtp": {
#     "host": "'"$MAIL_HOST"'",
#     "port": "'"$MAIL_PORT"'",
#     "encryption": "'"$MAIL_ENCRYPTION"'",
#     "auth": true,
#     "user": "'"$MAIL_USER"'",
#     "pass": "'"$MAIL_PASS"'"
#   }
# }' --format=json

# echo "Install WP-WebAuthn und WP-Members ..."
# $WPCLI plugin install wp-members wp-webauthn really-simple-captcha --activate
# echo "Lösche Standard-Plugins ..."
# $WPCLI plugin delete akismet hello

# echo "Setze Eigentümer auf www-data (33:33)"
# sudo chown -R 33:33 ./wp_data
# sudo find ./wp_data -type d -exec chmod 755 {} \;
# sudo find ./wp_data -type f -exec chmod 644 {} \;

# docker compose up -d

# echo "Testmail an robert.lichtenegger@icloud.com senden ..."
# docker compose exec wp-cli wp eval "
# wp_mail(
#   'robert.lichtenegger@icloud.com',
#   'SMTP Test',
#   'Testmail aus WP-CLI (Docker)'
# );" --allow-root


