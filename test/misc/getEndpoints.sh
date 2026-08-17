# Ersetze diese Variablen mit deinen tatsächlichen Werten
WORDPRESS_DOMAIN="localhost:8080"

curl -X GET -u "robert:9wxmmokYCHhhrR18X1dvy91L" \
  -H "Accept: application/json" \
  "http://${WORDPRESS_DOMAIN}/wp-json/wp/v2/"