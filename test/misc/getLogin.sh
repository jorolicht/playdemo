# Ersetze diese Variablen mit deinen tatsächlichen Werten
WORDPRESS_DOMAIN="localhost:8080"


# curl -X GET -u "robert:9wxmmokYCHhhrR18X1dvy91L" \
#   -H "Accept: application/json" \
#   "http://${WORDPRESS_DOMAIN}/wp-json/playdemo/v1/user"  


curl -X GET \
  -H "Accept: application/json" \
  -H "X-WP-Nonce: b5d8f5e97b" \
  "http://${WORDPRESS_DOMAIN}/wp-json/playdemo/v1/user"  