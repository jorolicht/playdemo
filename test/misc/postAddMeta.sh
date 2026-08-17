# Ersetze diese Variablen mit deinen tatsächlichen Werten
WORDPRESS_DOMAIN="localhost:8080"


curl -X POST -u "robert:9wxmmokYCHhhrR18X1dvy91L" \
  -H "Content-Type: application/json" \
  -d '{
        "post_type": "playdemo",
        "meta_key": "cfield1",
        "type": "string",
        "single": true,
        "show_in_rest": true
      }' \
  "http://${WORDPRESS_DOMAIN}/wp-json/playdemo/v1/register-meta"

  # ${Page_ID}"

  # "http://${WORDPRESS_DOMAIN}/wp-json/playdemo/v1/register-meta" 
