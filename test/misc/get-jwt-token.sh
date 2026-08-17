# Ersetze diese Variablen mit deinen tatsächlichen Werten
WORDPRESS_DOMAIN="localhost:8080"
USER="robert"
PASSWORD="mx5dkwkidnTmNavhDnSeVmqK"

curl -v -X POST \
  -u ${USER}:${PASSWORD} \
  http://${WORDPRESS_DOMAIN}/wp-json/tourney/v1/get-jwt-token
