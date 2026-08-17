# Ersetze diese Variablen mit deinen tatsächlichen Werten
WORDPRESS_DOMAIN="localhost:8080"


Page_ID=$(curl -X GET -u "robert:9wxmmokYCHhhrR18X1dvy91L" \
  -H "Accept: application/json" \
  "http://${WORDPRESS_DOMAIN}/wp-json/wp/v2/pages?slug=config-data" | jq '.[0].id')   


echo "Die ID des Posts ist: ${POST_ID}"  