#!/bin/bash
# Shell test script for Payhip Webhook API endpoint

WORDPRESS_DOMAIN="localhost:8080"
EMAIL="robert" # Default admin/test email or username

echo "=== 1. Testing Payhip Webhook (Missing Email) ==="
curl -i -X POST "http://${WORDPRESS_DOMAIN}/wp-json/payhip/v1/webhook" \
     -H "Content-Type: application/json" \
     -d '{}'

echo -e "\n\n=== 2. Testing Payhip Webhook (User Not Found) ==="
curl -i -X POST "http://${WORDPRESS_DOMAIN}/wp-json/payhip/v1/webhook" \
     -H "Content-Type: application/json" \
     -d '{
       "email": "nonexistent_user_99999@example.com",
       "product_name": "3 Turniere"
     }'

echo -e "\n\n=== 3. Testing Payhip Webhook (Valid Purchase - 3 Tournaments) ==="
curl -i -X POST "http://${WORDPRESS_DOMAIN}/wp-json/payhip/v1/webhook" \
     -H "Content-Type: application/json" \
     -d '{
       "email": "admin@example.com",
       "product_name": "3 Turniere Cloud Live Pro",
       "quantity": 1
     }'
