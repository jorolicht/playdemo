# Ersetze diese Variablen mit deinen tatsächlichen Werten
WORDPRESS_DOMAIN="localhost:9555"
SLUG_PARAM="playdemo_slug"

curl -X PUT -u "robert:9wxmmokYCHhhrR18X1dvy91L"  "http://${WORDPRESS_DOMAIN}/wp/post?slug=${SLUG_PARAM}&field=data_1" -H "Content-Type: application/json" -d '{ "title": "Titel des ersten Beitrags", "author": "Max Mustermann", "tags": ["Scala", "Play Framework"] }' 
