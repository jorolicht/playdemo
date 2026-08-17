#!/bin/bash

# Dieses Skript wandelt einen Custom Post Type 'tourney' in eine normale Wordpress Seite um.
# Verwendung: ./convertTourney2Page.sh <post_id_oder_slug>

if [ -z "$1" ]; then
  echo "Fehler: Bitte geben Sie eine Post-ID oder einen Slug an."
  echo "Verwendung: $0 <post_id_oder_slug>"
  exit 1
fi

INPUT=$1

# Konfiguration (kann durch Umgebungsvariablen überschrieben werden)
WP_DOMAIN=${WORDPRESS_DOMAIN:-"localhost:8080"}
WP_USER=${USER:-"robert"}
WP_PWD=${PASSWORD:-"x0CsysbewmNvehrtOEnCgsuT"}
WP_PROTO=${WORDPRESS_PROTO:-"http"}

# Zusätzliche Curl-Optionen (z.B. -k für selbstsignierte Zertifikate bei https)
CURL_OPTS=""
if [ "$WP_PROTO" = "https" ]; then
  CURL_OPTS="-k"
fi

# Prüfen ob Input eine Zahl (ID) oder ein String (Slug) ist
if [[ $INPUT =~ ^[0-9]+$ ]]; then
  DATA="{\"postId\": $INPUT}"
  echo "Wandle Turnier (ID $INPUT) in eine Seite ('page') um auf ${WP_PROTO}://${WP_DOMAIN}..."
else
  DATA="{\"slug\": \"$INPUT\"}"
  echo "Wandle Turnier (Slug '$INPUT') in eine Seite ('page') um auf ${WP_PROTO}://${WP_DOMAIN}..."
fi

curl $CURL_OPTS -s -X POST \
  -u "${WP_USER}:${WP_PWD}" \
  -H "Content-Type: application/json" \
  -d "$DATA" \
  "${WP_PROTO}://${WP_DOMAIN}/wp-json/tourney/v1/convert-to-page" | jq .

echo -e "\nFertig."
