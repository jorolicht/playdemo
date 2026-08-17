#!/bin/bash
set -euo pipefail

# Configuration
BASE_URL="${BASE_URL:-https://localhost/wp-json}"
WEBHOOK_URL="${BASE_URL}/payhip/v1/webhook"
TEST_EMAIL="${TEST_EMAIL:-robert.lichtenegger@gmail.com}"
CURL_OPTS="-k -s -L"

echo "============================================================"
echo "🧪 Teste Payhip Webhook-Endpunkt (${WEBHOOK_URL})"
echo "============================================================"

# Test 1: Senden eines gültigen Payhip-Kauf-Webhooks (1 Turnier)
echo -e "\n1. POST Webhook: 1 Turnier gekauft per 'tourneys' Feld ($TEST_EMAIL)"
PAYLOAD_1=$(cat <<EOF
{
  "email": "${TEST_EMAIL}",
  "tourneys": 1,
  "price": 8.90,
  "product_name": "Single Tournament License"
}
EOF
)

RES_1=$(curl $CURL_OPTS -X POST "${WEBHOOK_URL}" \
  -H "Content-Type: application/json" \
  -d "${PAYLOAD_1}")

echo "Antwort: ${RES_1}"

# Test 2: Senden eines Payhip-Webhooks mit Produktname "3 Turniere Paket"
echo -e "\n2. POST Webhook: 3 Turniere gekauft per 'product_name' Parse ($TEST_EMAIL)"
PAYLOAD_2=$(cat <<EOF
{
  "customer_email": "${TEST_EMAIL}",
  "product_name": "3 Turniere Paket Pro",
  "price": 24.90
}
EOF
)

RES_2=$(curl $CURL_OPTS -X POST "${WEBHOOK_URL}" \
  -H "Content-Type: application/json" \
  -d "${PAYLOAD_2}")

echo "Antwort: ${RES_2}"

# Test 3: Senden ohne E-Mail (Fehlertest - 400 Bad Request erwartet)
echo -e "\n3. POST Webhook: Ungültiger Aufruf ohne E-Mail (Fehlertest - HTTP 400 erwartet)"
PAYLOAD_3='{"tourneys": 5}'

RES_3=$(curl $CURL_OPTS -w "\nHTTP-Code: %{http_code}" -X POST "${WEBHOOK_URL}" \
  -H "Content-Type: application/json" \
  -d "${PAYLOAD_3}")

echo "Antwort:"
echo "${RES_3}"

# Test 4: Senden mit nicht existierendem User (Fehlertest - 404 Not Found erwartet)
echo -e "\n4. POST Webhook: Unbekannter User (Fehlertest - HTTP 404 erwartet)"
PAYLOAD_4='{"email": "unbekannter_user_123456789@example.com", "count": 2}'

RES_4=$(curl $CURL_OPTS -w "\nHTTP-Code: %{http_code}" -X POST "${WEBHOOK_URL}" \
  -H "Content-Type: application/json" \
  -d "${PAYLOAD_4}")

echo "Antwort:"
echo "${RES_4}"

echo -e "\n============================================================"
echo "✅ Payhip Webhook Test-Script beendet."
echo "============================================================"
