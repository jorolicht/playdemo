. "$(dirname "$0")/../credentials.env"   # TEST_WP_USER, TEST_WP_PASSWORD (Vorlage: test/credentials.env.example)
# Ersetze diese Variablen mit deinen tatsächlichen Werten
WORDPRESS_DOMAIN="localhost:8080"
USER="$TEST_WP_USER"
PASSWORD="$TEST_WP_PASSWORD"

curl -X POST \
  -u ${USER}:${PASSWORD} \
  -H "Content-Type: application/json" \
  -d '{"post_id":11,"target_type":"tourney"}' \
  http://localhost:8080/wp-json/tourney/v1/convert-to-cpt 
  

