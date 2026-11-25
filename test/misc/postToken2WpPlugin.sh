. "$(dirname "$0")/../credentials.env"   # TEST_WP_USER, TEST_WP_PASSWORD (Vorlage: test/credentials.env.example)
# Test Anwendungspasswort
# PlaydemoPwName

curl -X POST http://localhost:8080/wp-json/jwt-auth/v1/token -H 'Content-Type: application/json' -d "{ \"username\": \"${TEST_WP_USER}\", \"password\": \"${TEST_WP_PASSWORD}\" }"
