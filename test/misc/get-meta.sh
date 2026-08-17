# Ersetze diese Variablen mit deinen tatsächlichen Werten
WORDPRESS_DOMAIN="localhost:8080"
USER="robert"
PASSWORD="uV2S0HBJXKL50kZ6EMrSeVdM"

# curl -X POST -u "${USER}:${PASSWORD}"  "http://${WORDPRESS_DOMAIN}/wp-json/mein-tool/v1/save-json/test4-eintrag" \
#      -H "Content-Type: application/json" \
#      -d '{"titel": "Hallo Welt4", "content": "JSON Daten"}'

curl -X GET "http://${WORDPRESS_DOMAIN}/wp-json/playdemo/v1/get-meta/testseite?key=object_typ" \
     -u "${USER}:${PASSWORD}"


curl -X GET "https://your-site.com/wp-json/your-namespace/v1/tourney-get-clubs?postId=123&metafield-name=clubs" \
  -H "Content-Type: application/json"