# Ersetze diese Variablen mit deinen tatsächlichen Werten
WORDPRESS_DOMAIN="localhost:8080"


# Dein JSON-Inhalt, als String
//JSON_DATA='{\"produktId\": \"ABC123\", \"preis\": 99.99, \"verfuegbar\": true, \"tags\": [\"elektronik\", \"angebot\"]}'

//JS_DATA=$(echo '{"text": "Zitat mit \" und \\ Zeichen"}' | jq -Rr @json)




Page_ID=$(curl -X GET -u "robert:PnK6aeahzx9dt4wlLHNTJYcH" \
  -H "Accept: application/json" \
  "http://${WORDPRESS_DOMAIN}/wp-json/wp/v2/playdemo?slug=playdemo_slug&status=private" | jq '.[0].id')   


echo "Die ID der Page ist: ${Page_ID}"  

curl -X POST -u "robert:PnK6aeahzx9dt4wlLHNTJYcH" \
  -H "Content-Type: application/json" \
  -d '{
        "meta": {
            "data_1": "{\"produktId\": \"222222ZZZZZ\", \"preis\": 99.99, \"verfuegbar\": true, \"tags\": [\"elektronik\", \"angebot\"]}"
        }
      }' \
  "http://${WORDPRESS_DOMAIN}/wp-json/wp/v2/playdemo/${Page_ID}" 

  # ${Page_ID}"

  # "http://${WORDPRESS_DOMAIN}/wp-json/wp/v2/playdemo/${Page_ID}" 
