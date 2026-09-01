#!/bin/bash

# Exit on error
set -e

# DOCKER_PROJECT_DIR is the directory containing this script
DOCKER_PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_PATH="$DOCKER_PROJECT_DIR/backups"
DATE=$(date +%Y-%m-%d_%H%M%S)

# Load environment variables
if [ -f "$DOCKER_PROJECT_DIR/.env" ]; then
    echo "Loading environment from .env..."
    while IFS= read -r line || [ -n "$line" ]; do
        # Strip comments starting with #
        clean_line=$(echo "$line" | sed 's/#.*//')
        # Trim leading/trailing whitespace
        clean_line=$(echo "$clean_line" | xargs)
        # Skip empty lines
        if [ -n "$clean_line" ]; then
            export "$clean_line" 2>/dev/null || true
        fi
    done < "$DOCKER_PROJECT_DIR/.env"
fi

# Configuration
CONTAINER_DB="wp-db-instance"
DB_USER="root"
DB_PASS="${DB_ROOT_PASSWORD:-}"

if [ -z "$DB_PASS" ]; then
    echo "❌ Fehler: DB_ROOT_PASSWORD ist nicht in .env definiert!"
    exit 1
fi

# Google Drive Info
GDRIVE_REMOTE="${GDRIVE_REMOTE:-gooDrive}"
GDRIVE_FOLDER="${GDRIVE_FOLDER:-backup_rolicht}"
RETENTION_DAYS="${RETENTION_DAYS:-3}"

# Ensure backup directory exists
mkdir -p "$BACKUP_PATH"

# 1. Datenbank-Dump
echo "Sichere Datenbank..."
docker exec -i $CONTAINER_DB /usr/bin/mariadb-dump -u$DB_USER -p$DB_PASS --all-databases > $BACKUP_PATH/db_$DATE.sql

# 2. Dateisystem (wp-content) sichern
echo "Sichere WordPress-Dateien..."
tar --exclude='cache' -czf $BACKUP_PATH/files_$DATE.tar.gz \
  -C $DOCKER_PROJECT_DIR docker-compose.yml \
  -C $DOCKER_PROJECT_DIR/wp_data wp-config.php \
  -C $DOCKER_PROJECT_DIR/wp_data/wp-content .

# 3. Upload zu Google Drive
if command -v rclone &> /dev/null; then
    if rclone listremotes | grep -q "^${GDRIVE_REMOTE}:"; then
        echo "Übertrage zu Google Drive..."
        rclone copy "$BACKUP_PATH/db_$DATE.sql" "$GDRIVE_REMOTE:$GDRIVE_FOLDER/$DATE/"
        rclone copy "$BACKUP_PATH/files_$DATE.tar.gz" "$GDRIVE_REMOTE:$GDRIVE_FOLDER/$DATE/"
    else
        echo "WARNUNG: rclone-Remote '$GDRIVE_REMOTE' wurde in rclone nicht gefunden. Überspringe Upload."
    fi
else
    echo "WARNUNG: rclone ist nicht installiert. Überspringe Upload zu Google Drive."
fi

# 4. Alte Backups löschen (älter als X Tage)
echo "Lösche lokale Backups, die älter als $RETENTION_DAYS Tage sind..."
find "$BACKUP_PATH" -type f -mtime +"$RETENTION_DAYS" -delete

echo "Backup erfolgreich abgeschlossen: $DATE"
