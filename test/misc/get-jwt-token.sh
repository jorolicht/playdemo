. "$(dirname "$0")/../credentials.env"   # TEST_WP_USER, TEST_WP_PASSWORD (Vorlage: test/credentials.env.example)
# Ersetze diese Variablen mit deinen tatsächlichen Werten
WORDPRESS_DOMAIN="localhost:8080"
USER="$TEST_WP_USER"
PASSWORD="$TEST_WP_PASSWORD"

curl -v -X POST \
  -u ${USER}:${PASSWORD} \
  http://${WORDPRESS_DOMAIN}/wp-json/tourney/v1/get-jwt-token
