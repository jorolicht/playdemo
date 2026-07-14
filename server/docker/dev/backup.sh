#!/bin/bash

# ==============================================================================
# WordPress & Database Backup Script
# ==============================================================================

# 1. Configuration
BACKUP_DIR="./backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
DB_CONTAINER="wp-db-instance"
WP_DATA_DIR="./wp_data"

# Attempt to find and load environment variables
ENV_FILES=("../../env/.env" "../../env/dev.env" ".env")
for ENV_FILE in "${ENV_FILES[@]}"; do
    if [ -f "$ENV_FILE" ]; then
        echo "Loading environment from $ENV_FILE..."
        export $(grep -v '^#' "$ENV_FILE" | xargs)
        break
    fi
done

# Map variables (prioritize DB_ prefixed ones used in docker-compose)
BACKUP_DB_NAME=${DB_NAME:-wordpress}
BACKUP_DB_USER="root"
BACKUP_DB_PASS=${DB_ROOT_PASSWORD:-$MYSQL_ROOT_PASSWORD}

# Fallback to non-root user if root password is not set
if [ -z "$BACKUP_DB_PASS" ]; then
    BACKUP_DB_USER=${DB_USER:-$MYSQL_USER}
    BACKUP_DB_PASS=${DB_PASSWORD:-$MYSQL_PASSWORD}
fi

if [ -z "$BACKUP_DB_PASS" ]; then
    echo "ERROR: Database password not found in environment variables."
    exit 1
fi

mkdir -p "$BACKUP_DIR"

echo "--- Starting Backup: $TIMESTAMP ---"

# 2. Database Backup (SQL Dump)
echo "Backing up database '$BACKUP_DB_NAME' as user '$BACKUP_DB_USER'..."
FILE_DB="$BACKUP_DIR/db_${BACKUP_DB_NAME}_${TIMESTAMP}.sql.gz"

# Use MYSQL_PWD to avoid "password on command line" warning and expansion issues
docker exec -e MYSQL_PWD="$BACKUP_DB_PASS" "$DB_CONTAINER" /usr/bin/mariadb-dump -u"$BACKUP_DB_USER" "$BACKUP_DB_NAME" | gzip > "$FILE_DB"

if [ ${PIPESTATUS[0]} -eq 0 ]; then
    echo "Database backup saved to: $FILE_DB"
else
    echo "ERROR: Database backup failed!"
    rm -f "$FILE_DB"
    exit 1
fi

# 3. WordPress Files Backup (wp_data)
echo "Backing up WordPress files..."
FILE_WP="$BACKUP_DIR/wp_files_${TIMESTAMP}.tar.gz"

tar -czf "$FILE_WP" "$WP_DATA_DIR"

if [ $? -eq 0 ]; then
    echo "WordPress files backup saved to: $FILE_WP"
else
    echo "ERROR: File backup failed!"
    exit 1
fi

echo "--- Backup Completed Successfully ---"
exit 0
