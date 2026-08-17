#!/bin/bash

# Dieses Skript wandelt eine Wordpress Seite in einen Custom Post Type 'tourney' um.
# Verwendung: ./convertPage2Tourney.sh <post_id_oder_slug>

if [ -z "$1" ]; then
  echo "Fehler: Bitte geben Sie eine Post-ID oder einen Slug an."
  echo "Verwendung: $0 <post_id_oder_slug>"
  exit 1
fi

INPUT=$1
TARGET_TYPE="tourney"

# Konfiguration (kann durch Umgebungsvariablen überschrieben werden)
WP_DOMAIN=${WORDPRESS_DOMAIN:-"localhost"}
WP_USER=${USER:-"robert"}
WP_PWD=${PASSWORD:-"7utE56CyUbYzH5LylhGiYlKt"}
WP_PROTO=${WORDPRESS_PROTO:-"https"}

# Zusätzliche Curl-Optionen (z.B. -k für selbstsignierte Zertifikate bei https)
CURL_OPTS=""
if [ "$WP_PROTO" = "https" ]; then
  CURL_OPTS="-k"
fi

# Prüfen ob Input eine Zahl (ID) oder ein String (Slug) ist
if [[ $INPUT =~ ^[0-9]+$ ]]; then
  DATA="{\"post_id\": $INPUT, \"target_type\": \"$TARGET_TYPE\"}"
  echo "Wandle Post ID $INPUT in CPT '$TARGET_TYPE' um auf ${WP_PROTO}://${WP_DOMAIN}..."
else
  DATA="{\"slug\": \"$INPUT\", \"target_type\": \"$TARGET_TYPE\"}"
  echo "Wandle Slug '$INPUT' in CPT '$TARGET_TYPE' um auf ${WP_PROTO}://${WP_DOMAIN}..."
fi

curl $CURL_OPTS -X POST \
  -u "${WP_USER}:${WP_PWD}" \
  -H "Content-Type: application/json" \
  -d "$DATA" \
  "${WP_PROTO}://${WP_DOMAIN}/wp-json/tourney/v1/convert-to-cpt"

echo -e "\nFertig."
