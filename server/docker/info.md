# Important Docker Compose commands:

## Start / Stop
docker compose -f docker_compose.yml --env-file ./env/docker.env up -d
docker compose -f docker_compose.yml --env-file ./env/docker.env down

## Build Image 
docker compose -f docker_compose.yml --env-file ./env/docker.env up build -d
docker compose --env-file ../env/docker.env up -d --build playsrv


## Step into Container 
docker exec -it playsrv-instance /bin/bash

## Export WP-Members settings and fields from WordPress database to JSON file:
docker exec wp-cli-instance wp option get wpmembers_settings --format=json --allow-root > wpmembers_settings.json
docker exec wp-cli-instance wp option get wpmembers_fields --format=json --allow-root > wpmembers_fields.json

## Konfiguration einspielen via setup.sh

In deinem Setup-Skript kannst du diese Dateien nun nutzen, um die Konfiguration automatisch in einen frischen Container zu pushen.

```
echo "⚙️  Importing WP-Members configuration..."

# Pfad zu deinen JSON-Dateien
SETTINGS_FILE="wpmembers_settings.json"
FIELDS_FILE="wpmembers_fields.json"

# Einstellungen importieren
cat "$SETTINGS_FILE" | docker exec -i wp-cli-instance wp option update wpmembers_settings --format=json

# Felder importieren
cat "$FIELDS_FILE" | docker exec -i wp-cli-instance wp option update wpmembers_fields --format=json

echo "✅ WP-Members configuration applied."
```

## HCaptcha site und secret key einspielen
Nutzt die Variablen aus deinem geladenen .env File
```
docker exec wp-cli-instance wp option patch update wpmembers_settings recaptcha_site_key "$HCAPTCHA_SITE_KEY"
docker exec wp-cli-instance wp option patch update wpmembers_settings recaptcha_secret_key "$HCAPTCHA_SECRET_KEY"
```

## Seiten-Zuweisung automatisieren
WP-Members muss wissen, welche Page-ID die "Login"-Seite hat. Da IDs sich ändern können, ist es sicherer, die ID dynamisch über den Slug zu suchen:

- Sucht die ID der Seite mit dem Slug 'login'
LOGIN_PAGE_ID=$(docker exec wp-cli-instance wp post list --post_type=page --name=login --format=ids)

- Weist diese ID in den WP-Members Settings zu
docker exec wp-cli-instance wp option patch update wpmembers_settings login_page "$LOGIN_PAGE_ID"


## Seiten sichern
- Inhalt der Login-Seite sichern (Slug 'login')
docker exec wp-cli-instance wp post list --post_type=page --name=login --field=post_content > page_login_content.txt

- Inhalt der Registrierungsseite sichern (Slug 'register')
docker exec wp-cli-instance wp post list --post_type=page --name=register --field=post_content > page_register_content.txt


## Die Seiten im setup.sh automatisch erstellen
```
echo "📄 Erstelle Membership Seiten..."
# 1. Login Seite erstellen (falls nicht vorhanden)
LOGIN_ID=$(docker exec -i wp-cli-instance wp post create \
    --post_type=page \
    --post_title='Login' \
    --post_status=publish \
    --post_name=login \
    --post_content="$(cat ./page_login_content.txt)" \
    --porcelain)

# 2. Registrierung Seite erstellen
REG_ID=$(docker exec -i wp-cli-instance wp post create \
    --post_type=page \
    --post_title='Registrierung' \
    --post_status=publish \
    --post_name=register \
    --post_content="$(cat ./page_register_content.txt)" \
    --porcelain)
```    

## Zuweisung in WP-Members automatisieren
Jetzt kommt der entscheidende Schritt: Du musst WP-Members mitteilen, dass diese neu erstellten IDs die offiziellen Login/Register-Seiten sind. Dies geschieht über das Patching der wpmembers_settings Option:

```
echo "⚙️ Verknüpfe Seiten mit WP-Members..."

# Zuweisung der Login-Seite
docker exec wp-cli-instance wp option patch update wpmembers_settings login_page "$LOGIN_ID"

# Zuweisung der Registrierungsseite
docker exec wp-cli-instance wp option patch update wpmembers_settings register_page "$REG_ID"
```

# Create Application Password
```
docker exec wp-cli-instance wp  user application-password create robert myapp --allow-root | awk '/Password: /{print $2}'
```