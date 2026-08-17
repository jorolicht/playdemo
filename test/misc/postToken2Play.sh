# Ersetze diese Variablen mit deinen tatsächlichen Werten
PLAY_DOMAIN="localhost:9555"

curl -X POST \
  -H "Content-Type: application/json" \
  -d '{
        "user": "pUser",
        "club": "pClub",
        "apName": "aUser",
        "apPassword": "aPassword"
      }' \
  "http://${PLAY_DOMAIN}/wp/token"
