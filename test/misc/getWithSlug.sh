. "$(dirname "$0")/../credentials.env"   # TEST_WP_USER, TEST_WP_PASSWORD (Vorlage: test/credentials.env.example)
# Ersetze diese Variablen mit deinen tatsächlichen Werten
WORDPRESS_DOMAIN="localhost:8080"


Page_ID=$(curl -X GET -u "${TEST_WP_USER}:${TEST_WP_PASSWORD}" \
  -H "Accept: application/json" \
  "http://${WORDPRESS_DOMAIN}/wp-json/wp/v2/pages?slug=config-data" | jq '.[0].id')   


echo "Die ID des Posts ist: ${POST_ID}"  