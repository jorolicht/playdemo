# Ersetze diese Variablen mit deinen tatsächlichen Werten
WORDPRESS_DOMAIN="localhost:8080"


# Dein JSON-Inhalt, als String
JSON_DATA='{"produktId": "ABC123", "preis": 99.99, "verfuegbar": true, "tags": ["elektronik", "angebot"]}'


Page_ID=$(curl -X GET -u "robert:9wxmmokYCHhhrR18X1dvy91L" \
  -H "Accept: application/json" \
  "http://${WORDPRESS_DOMAIN}/wp-json/wp/v2/pages?slug=config-data" | jq '.[0].id')   


echo "Die ID der Page ist: ${Page_ID}"  

curl -X POST -u "robert:9wxmmokYCHhhrR18X1dvy91L" \
  -H "Content-Type: application/json" \
  -d '{
        "title": "Seite mit JSON-DatenSlut",
        "content": "<p>Diese Seite enthält Produktinformationen als JSON im Custom Field.</p>",
        "status": "publish",
        "slug":   "config-data",
        "meta": {
            "my_json_data": '"${JSON_DATA}"'
        }
      }' \
  "http://${WORDPRESS_DOMAIN}/wp-json/wp/v2/pages/${Page_ID}" 
