. "$(dirname "$0")/../credentials.env"   # TEST_WP_USER, TEST_WP_PASSWORD (Vorlage: test/credentials.env.example)
curl -X POST -u "${TEST_WP_USER}:${TEST_WP_PASSWORD}"  \
  -F "file=@example.json;type=application/json"  \
  "http://localhost:8080/wp-json/wp/v2/media"