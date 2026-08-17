# Ersetze diese Variablen mit deinen tatsächlichen Werten
WORDPRESS_DOMAIN="localhost:8080"
USER="robert"
PASSWORD="uV2S0HBJXKL50kZ6EMrSeVdM"

# curl -X POST -u "${USER}:${PASSWORD}"  "http://${WORDPRESS_DOMAIN}/wp-json/mein-tool/v1/save-json/test4-eintrag" \
#      -H "Content-Type: application/json" \
#      -d '{"titel": "Hallo Welt4", "content": "JSON Daten"}'


# curl -X POST "http://${WORDPRESS_DOMAIN}/wp-json/mein-tool/v1/save-json/robert-test/person" \
#      -u "${USER}:${PASSWORD}"  \
#      -H "Content-Type: application/json" \
#      -d '{
#           "titel": "Profil von Robert",
#           "inhalt": {
#             "person": {
#               "name": "Robert",
#               "alter": 35,
#               "beruf": "Entwickler"
#             }
#           }
#          }'     


curl -X POST "http://${WORDPRESS_DOMAIN}/wp-json/tourney/v1/save-json/ttc/2026_02_01/123_person" \
     -u "${USER}:${PASSWORD}"  \
     -H "Content-Type: application/json" \
     -d '{
          "titel": "Profil von Robert123",
          "content": {
            "person": {
              "name": "Robert",
              "alter": 35,
              "beruf": "Top EntwicklerX"
            }
          }
         }'       

curl -X POST "http://${WORDPRESS_DOMAIN}/wp-json/tourney/v1/save-json/ttc/2026_02_01/124_person" \
     -u "${USER}:${PASSWORD}"  \
     -H "Content-Type: application/json" \
     -d '{
          "titel": "Profil von Robert124",
          "content": {
            "person": {
              "name": "Robert124",
              "alter": 35,
              "beruf": "Top Entwickler"
            }
          }
         }'                     