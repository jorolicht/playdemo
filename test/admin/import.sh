#!/bin/bash

# Dieses Skript importiert alle Turnier-Metadaten aus einer JSON-Datei in einen WordPress-Post.
# Verwendung: ./import.sh <post_id_oder_slug> <input_file.json>

if [ -z "$1" ] || [ -z "$2" ]; then
  echo "Fehler: Post-ID/Slug oder Input-Datei fehlt."
  echo "Verwendung: $0 <post_id_oder_slug> <input_file.json>"
  exit 1
fi

INPUT=$1
INPUT_FILE=$2

if [ ! -f "$INPUT_FILE" ]; then
  echo "Fehler: Datei $INPUT_FILE nicht gefunden."
  exit 1
fi

# Konfiguration
WP_DOMAIN=${WORDPRESS_DOMAIN:-"localhost"}
WP_USER=${USER:-"robert"}
WP_PWD=${PASSWORD:-"7utE56CyUbYzH5LylhGiYlKt"}
WP_PROTO=${WORDPRESS_PROTO:-"https"}
CURL_OPTS="-s"
if [ "$WP_PROTO" = "https" ]; then CURL_OPTS="-sk"; fi

# Bestimme ob Parameter postId oder slug ist
PARAM_NAME="postId"
if [[ ! $INPUT =~ ^[0-9]+$ ]]; then
  PARAM_NAME="slug"
fi

echo "Importiere Turnier-Daten aus $INPUT_FILE in $INPUT auf ${WP_PROTO}://${WP_DOMAIN}..."

# Hilfsfunktion zum Senden der Daten
sync_data() {
  local route=$1
  local payload=$2
  curl $CURL_OPTS -X POST \
    -u "${WP_USER}:${WP_PWD}" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "${WP_PROTO}://${WP_DOMAIN}/wp-json/tourney/v1/${route}?${PARAM_NAME}=${INPUT}"
}

# Hilfsfunktion zum Abrufen der aktuellen Version vom Server
get_server_version() {
  local route=$1
  local res
  res=$(curl $CURL_OPTS -u "${WP_USER}:${WP_PWD}" "${WP_PROTO}://${WP_DOMAIN}/wp-json/tourney/v1/${route}?${PARAM_NAME}=${INPUT}")
  echo "$res" | grep -o '"version":[0-9]*' | head -1 | cut -d: -f2
}

# Wir nutzen jq für das Parsing
if ! command -v jq >/dev/null 2>&1; then
  echo "Fehler: Dieses Skript benötigt 'jq' für das JSON-Parsing."
  exit 1
fi

# 1. Basisdaten (Tourney)
echo "Aktualisiere Basisdaten..."
VERSION=$(get_server_version "read")
if [ -z "$VERSION" ]; then
  echo "Turnier existiert nicht. Erstelle neues Turnier mit Slug '$INPUT'..."
  # Extrahiere das Turnier-Objekt und füge den gewünschten Slug hinzu
  CREATE_PAYLOAD=$(jq --arg slug "$INPUT" '.tourney.tourney | .slug = $slug | .wpId = 0' "$INPUT_FILE")
  
  # Rufe den /create-Endpunkt auf
  CREATE_RES=$(curl $CURL_OPTS -X POST \
    -u "${WP_USER}:${WP_PWD}" \
    -H "Content-Type: application/json" \
    -d "$CREATE_PAYLOAD" \
    "${WP_PROTO}://${WP_DOMAIN}/wp-json/tourney/v1/create")
    
  PAGE_ID=$(echo "$CREATE_RES" | grep -o '"pageId":[0-9]*' | head -1 | cut -d: -f2)
  if [ -z "$PAGE_ID" ] || [ "$PAGE_ID" = "null" ]; then
    echo "Fehler beim Erstellen des Turniers: $CREATE_RES"
    exit 1
  fi
  echo "Turnier erfolgreich erstellt mit ID: $PAGE_ID"
  
  VERSION=1
  INPUT=$PAGE_ID
  PARAM_NAME="postId"
fi

PAYLOAD=$(jq --arg v "$VERSION" '.tourney | .version = ($v|tonumber)' "$INPUT_FILE")
sync_data "tourney-sync" "$PAYLOAD"

# 2. Vereine (Clubs)
echo "Aktualisiere Vereine..."
VERSION=$(get_server_version "clubs")
PAYLOAD=$(jq -n --arg v "$VERSION" --argjson clubs "$(jq '.tourney.tourney.clubs' "$INPUT_FILE")" '{version: ($v|tonumber), clubs: $clubs}')
sync_data "clubs-sync" "$PAYLOAD"

# 3. Spieler (Players)
echo "Aktualisiere Spieler..."
VERSION=$(get_server_version "players")
PAYLOAD=$(jq -n --arg v "$VERSION" --argjson players "$(jq '.tourney.tourney.players' "$INPUT_FILE")" '{version: ($v|tonumber), players: $players}')
sync_data "players-sync" "$PAYLOAD"

# 4. Wettbewerbe (Competitions)
echo "Aktualisiere Wettbewerbe..."
PAYLOAD=$(jq -n --argjson events "$(jq '(.tourney.tourney.competitions // []) | map(select(. != null))' "$INPUT_FILE")" '{events: $events}')
sync_data "competitions-sync" "$PAYLOAD"

# 5. Runden (Rounds)
echo "Aktualisiere Runden..."
PAYLOAD=$(jq -n --argjson events "$(jq '(.tourney.tourney.stages // []) | map(select(. != null))' "$INPUT_FILE")" '{events: $events}')
sync_data "rounds-sync" "$PAYLOAD"

# 6. Extra-Metafelder (StartDatum, EndDatum, Ident, Category, Organisator)
echo "Aktualisiere Extra-Metafelder..."
PAYLOAD=$(jq '.extraMeta' "$INPUT_FILE")
if [ "$PAYLOAD" != "null" ]; then
    sync_data "meta-data" "$PAYLOAD"
fi

echo -e "\nImport erfolgreich abgeschlossen."
