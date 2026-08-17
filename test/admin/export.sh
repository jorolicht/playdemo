#!/bin/bash

# Dieses Skript exportiert alle Turnier-Metadaten eines WordPress-Posts in eine JSON-Datei.
# Verwendung: ./export.sh <post_id_oder_slug> [output_file.json]

if [ -z "$1" ]; then
  echo "Fehler: Bitte geben Sie eine Post-ID oder einen Slug an."
  echo "Verwendung: $0 <post_id_oder_slug> [output_file.json]"
  exit 1
fi

INPUT=$1
OUTPUT_FILE=${2:-"tourney_${INPUT}_export.json"}

# Konfiguration
WP_DOMAIN=${WORDPRESS_DOMAIN:-"localhost"}
WP_USER=${USER:-"robert"}
WP_PWD=${PASSWORD:-"mx5dkwkidnTmNavhDnSeVmqK"}
WP_PROTO=${WORDPRESS_PROTO:-"https"}
CURL_OPTS="-s"
if [ "$WP_PROTO" = "https" ]; then CURL_OPTS="-sk"; fi

# Bestimme ob Parameter postId oder slug ist
PARAM_NAME="postId"
if [[ ! $INPUT =~ ^[0-9]+$ ]]; then
  PARAM_NAME="slug"
fi

echo "Exportiere Turnier-Daten von $INPUT auf ${WP_PROTO}://${WP_DOMAIN}..."

# Hilfsfunktion zum Abrufen eines Meta-Bereichs
fetch_data() {
  local route=$1
  curl $CURL_OPTS -u "${WP_USER}:${WP_PWD}" "${WP_PROTO}://${WP_DOMAIN}/wp-json/tourney/v1/${route}?${PARAM_NAME}=${INPUT}"
}

echo "Lade Basisdaten (enthält Vereine, Spieler, Wettbewerbe, Runden)..."
TOURNEY=$(fetch_data "read")
echo "Lade Extra-Metafelder (StartDatum, EndDatum, Ident, Category, Organisator)..."
EXTRA_META=$(fetch_data "meta-data")

# Zusammenführen in eine JSON-Datei und Filterung von null-Werten in Wettbewerben und Runden
if command -v jq >/dev/null 2>&1; then
  jq -n \
    --argjson t "$TOURNEY" \
    --argjson ex "$EXTRA_META" \
    '{tourney: $t, extraMeta: $ex} | .tourney.tourney.competitions |= (if . then map(select(. != null)) else [] end) | .tourney.tourney.stages |= (if . then map(select(. != null)) else [] end)' > "$OUTPUT_FILE"
else
  echo "Warnung: jq nicht gefunden. Erstelle unformatiertes JSON manuell."
  echo "{\"tourney\": $TOURNEY, \"extraMeta\": $EXTRA_META}" > "$OUTPUT_FILE"
fi

echo "Export erfolgreich abgeschlossen: $OUTPUT_FILE"
