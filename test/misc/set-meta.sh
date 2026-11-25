. "$(dirname "$0")/../credentials.env"   # TEST_WP_USER, TEST_WP_PASSWORD (Vorlage: test/credentials.env.example)
# Ersetze diese Variablen mit deinen tatsächlichen Werten
WORDPRESS_DOMAIN="localhost:8080"
USER="$TEST_WP_USER"
PASSWORD="$TEST_WP_PASSWORD"
APP="test"
PASSWORD="$TEST_WP_PASSWORD"

# curl -X POST -u "${USER}:${PASSWORD}"  "http://${WORDPRESS_DOMAIN}/wp-json/mein-tool/v1/save-json/test4-eintrag" \
#      -H "Content-Type: application/json" \
#      -d '{"titel": "Hallo Welt4", "content": "JSON Daten"}'


curl -X POST "http://${WORDPRESS_DOMAIN}/wp-json/playdemo/v1/set-meta/testseite?key=object_typ" \
     -u "${USER}:${PASSWORD}" \
     -H "Content-Type: application/json" \
     -d '{
       "mode": "advanced",
       "theme": "dark",
       "retries": 39
     }' 