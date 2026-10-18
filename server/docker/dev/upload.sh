#!/bin/bash
# =============================================================================
# upload.sh - Fuegt Dateien zu den WordPress-Medien unter upload/download hinzu
#
# Verwendung:
#   ./upload.sh <Dateipfad> [weitere_Dateien...]
#
# Beschreibung:
#   Kopiert die uebergebene(n) Datei(en) in das Download-Verzeichnis von WordPress
#   (wp-content/uploads/download bzw. upload/download) und registriert sie mittels
#   WP-CLI in der WordPress-Mediathek, sodass sie in WordPress Medien sichtbar
#   sind und eine gueltige Attachment-URL erhalten.
# =============================================================================

set -euo pipefail
export LC_ALL=C

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST_UPLOAD_DIR="$SCRIPT_DIR/wp_data/wp-content/uploads/download"
CONTAINER_UPLOAD_DIR="/var/www/html/wp-content/uploads/download"

# 1. Parameterpruefung
if [ $# -eq 0 ]; then
    echo "Verwendung: $0 <Dateipfad> [weitere_Dateien...]"
    echo ""
    echo "Fuegt eine oder mehrere Dateien zu den WordPress-Medien unter"
    echo "upload/download (wp-content/uploads/download) hinzu."
    echo ""
    echo "Beispiel:"
    echo "  $0 Handbuch.pdf"
    echo "  $0 /pfad/zu/datei.zip bild.png"
    exit 1
fi

# 2. Pruefen, ob der Docker-Container wp-cli-instance laeuft
if ! docker ps --format '{{.Names}}' | grep -q "^wp-cli-instance$"; then
    echo "❌ Fehler: Docker-Container 'wp-cli-instance' laeuft nicht."
    echo "   Bitte starte die Umgebung zuerst mit: ./start.sh"
    exit 1
fi

# 3. Zielverzeichnis im Container vorbereiten (umgeht fehlende Host-Schreibrechte auf wp_data unter Linux)
docker exec -u root wp-cli-instance mkdir -p "$CONTAINER_UPLOAD_DIR"

# Sicherstellen, dass der Alias upload -> wp-content/uploads existiert
docker exec -u root wp-cli-instance sh -c '[ -e /var/www/html/upload ] || ln -s wp-content/uploads /var/www/html/upload'

# 4. Dateien verarbeiten und importieren
for INPUT_PATH in "$@"; do
    echo "------------------------------------------------------------"
    
    # Pfadaufloesung (relativ zum Aufrufort, relativ zum Skript oder bereits im Ziel)
    if [ -f "$INPUT_PATH" ]; then
        FILE_REALPATH="$(cd "$(dirname "$INPUT_PATH")" && pwd)/$(basename "$INPUT_PATH")"
    elif [ -f "$SCRIPT_DIR/$INPUT_PATH" ]; then
        FILE_REALPATH="$SCRIPT_DIR/$INPUT_PATH"
    elif [ -f "$HOST_UPLOAD_DIR/$INPUT_PATH" ]; then
        FILE_REALPATH="$HOST_UPLOAD_DIR/$INPUT_PATH"
    else
        echo "❌ Fehler: Datei '$INPUT_PATH' wurde nicht gefunden."
        continue
    fi

    FILENAME="$(basename "$FILE_REALPATH")"
    DEST_FILE="$HOST_UPLOAD_DIR/$FILENAME"
    CONTAINER_FILE="$CONTAINER_UPLOAD_DIR/$FILENAME"

    # Datei via docker cp ins Download-Verzeichnis kopieren, falls noch nicht dort liegend
    if [ "$FILE_REALPATH" != "$DEST_FILE" ]; then
        echo "📂 Kopiere '$FILENAME' nach '$CONTAINER_UPLOAD_DIR/'..."
        docker cp "$FILE_REALPATH" "wp-cli-instance:$CONTAINER_FILE"
    fi

    # Leserechte fuer Webserver im Container setzen
    docker exec -u root wp-cli-instance chmod 644 "$CONTAINER_FILE"
    docker exec -u root wp-cli-instance chown -R www-data:www-data "$CONTAINER_UPLOAD_DIR" 2>/dev/null || true

    # In die WordPress-Mediathek importieren
    echo "🔄 Registriere '$FILENAME' in WordPress-Medien..."
    ATTACHMENT_ID=$(docker exec wp-cli-instance wp media import "$CONTAINER_FILE" \
        --skip-copy \
        --porcelain \
        --allow-root \
        --path=/var/www/html)

    if [ -n "$ATTACHMENT_ID" ]; then
        ATTACHMENT_URL=$(docker exec wp-cli-instance wp eval "echo wp_get_attachment_url($ATTACHMENT_ID);" --allow-root --path=/var/www/html)
        echo "✅ Erfolgreich hinzugefuegt!"
        echo "   - Attachment ID: $ATTACHMENT_ID"
        echo "   - Dateiname:     $FILENAME"
        echo "   - Medien-URL:    $ATTACHMENT_URL"
        echo "   - Speicherort:   $CONTAINER_FILE"
    else
        echo "❌ Fehler beim Import von '$FILENAME'."
    fi
done
echo "------------------------------------------------------------"
