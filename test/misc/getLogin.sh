. "$(dirname "$0")/../credentials.env"   # TEST_WP_USER, TEST_WP_PASSWORD (Vorlage: test/credentials.env.example)
# Ersetze diese Variablen mit deinen tatsächlichen Werten
WORDPRESS_DOMAIN="localhost:8080"


# curl -X GET -u "${TEST_WP_USER}:${TEST_WP_PASSWORD}" \
#   -H "Accept: application/json" \
#   "http://${WORDPRESS_DOMAIN}/wp-json/playdemo/v1/user"  


curl -X GET \
  -H "Accept: application/json" \
  -H "X-WP-Nonce: b5d8f5e97b" \
  "http://${WORDPRESS_DOMAIN}/wp-json/playdemo/v1/user"  