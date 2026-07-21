#!/bin/bash

# Exit on error
set -e

# DOCKER_PROJECT_DIR is the directory containing this script
DOCKER_PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_PATH="$DOCKER_PROJECT_DIR/backups"
DATE=$(date +%Y%m%d_%H%M%S)

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
DB_PASS="${DB_ROOT_PASSWORD:-wpUserPw4577R}"

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

echo "Backup erfolgreich erstellt in: $BACKUP_PATH"
echo "- Datenbank: db_$DATE.sql"
echo "- WordPress-Dateien & docker-compose.yml: files_$DATE.tar.gz"
