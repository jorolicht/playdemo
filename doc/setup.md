# Anleitung: Setup der Produktivumgebung auf einem neuen Ubuntu Server

Diese Dokumentation beschreibt Schritt für Schritt alle notwendigen Maßnahmen, um die **Playdemo / Turnier-Service Produktivumgebung** auf einem frischen Ubuntu-Server (z. B. Ubuntu 24.04 LTS / 26.04 LTS) mit bereits installiertem Docker und Docker Compose in Betrieb zu nehmen.

---

## 1. Voraussetzungen

Vor dem Start sollten folgende Punkte sichergestellt sein:
1. **Ubuntu Server** mit SSH-Zugriff und ein eingerichteter Benutzer mit `sudo`-Rechten.
2. **Docker & Docker Compose v2** installiert (`docker --version` und `docker compose version`).
3. **Domain & DNS**: Die Domain (z. B. `rolicht.de`) zeigt per A-Record auf die öffentliche IP-Adresse des Servers.
4. **Netzwerk & Firewall (ufw)**: Ports `80` (HTTP), `443` (HTTPS) und `22` (SSH) sind geöffnet:
   ```bash
   sudo ufw allow 22/tcp
   sudo ufw allow 80/tcp
   sudo ufw allow 443/tcp
   sudo ufw enable
   ```

---

## 2. Systempakete & Werkzeuge installieren

Auf dem Server müssen Werkzeuge zum Entpacken, Klonen und Sichern installiert werden:

```bash
sudo apt update && sudo apt install -y \
  git \
  curl \
  unzip \
  tar \
  rclone \
  ca-certificates
```

---

## 3. Repository klonen & Verzeichnisstruktur vorbereiten

Klone das Projektarchiv in das gewünschte Zielverzeichnis (z. B. `~/server/playdemo` oder `/var/www/playdemo`):

```bash
mkdir -p ~/server
cd ~/server
git clone https://github.com/jorolicht/playdemo.git
cd playdemo
```


 ┃   Es reicht technisch aus, nur das Verzeichnis server/docker/prod auf den Produktivserver zu übertragen (z. B. per SCP, ZIP oder Git Sparse-Checkout).
 ┃
 ┃   ### Warum?
 ┃
 ┃   Auf dem Produktivserver wird der Quellcode (Scala, Play, Twirl usw.) nicht mehr kompiliert. Stattdessen zieht docker compose die fertig gebauten Docker-Images
 ┃   (jorolich/playsrv-image:1.0.2, wp-gmp-image, etc.) direkt von Docker Hub.
 ┃
 ┃   ### Gegenüberstellung der zwei Möglichkeiten:
 ┃
 ┃   1. Option A: Ganzes Repository klonen (Empfohlen für einfache Wartung)
 ┃       • Vorteil: Sehr einfach per git clone auszuführen und mit git pull auf dem Server aktuell zu halten.
 ┃       • Nachteil: Verbraucht etwas mehr Speicherplatz für Quellcode, der auf dem Produktivserver nicht direkt ausgeführt wird.
 ┃   2. Option B: Nur server/docker/prod auf den Server kopieren
 ┃       • Vorteil: Minimaler Speicherplatz, kein Quellcode auf dem Server.
 ┃       • Voraussetzung: Es werden nur die Dateien aus server/docker/prod (docker-compose.yml, .env, die Shell-Skripte und die Unterordner playsrv/, wordpress/,
 ┃       wp-cli/) auf dem Server benötigt.
 ┃       • Umsetzung per Git Sparse-Checkout (falls nur dieser Ordner per Git gezogen werden soll):
 ┃         mkdir playdemo-prod && cd playdemo-prod
 ┃         git init
 ┃         git remote add origin https://github.com/jorolicht/playdemo.git
 ┃         git config core.sparseCheckout true
 ┃         echo "server/docker/prod/*" >> .git/info/sparse-checkout
 ┃         git pull origin tourney
 ┃
 ┃


---

## 4. Produktiv-Umgebung konfigurieren (`server/docker/prod/.env`)

Navigiere in das Produktions-Docker-Verzeichnis:

```bash
cd server/docker/prod
```

Stelle sicher, dass die Datei `.env` im Ordner `server/docker/prod/` korrekt ausgefüllt ist. Erstelle oder passe die `.env` mit folgenden Parameter-Kategorien an:

```ini
# Domain & Basis-Einstellungen
MY_DOMAIN='rolicht.de'
MY_TITLE='Turnier Service'
MY_EMAIL='robert.lichtenegger@gmail.com'
MY_ADMIN_NAME='robert'
MY_ADMIN_PASS='DeinSicheresAdminPasswort!'

# Versionierung & Metadaten
APP_VERSION="1.0.2"
APP_NAME='TourneyApp'
APP_HOME='/app'
APP_DATE="2026-08-30"
APP_MAINTAINER="Robert Lichtenegger <robert.lichtenegger@gmail.com>"
APP_ORGANIZATION="org.turnier-service"
APP_ENVIRONMENT="production"

# Datenbank-Einstellungen (MariaDB / WordPress & Play Backend)
DB_NAME='wordpress'
DB_USER='wpuser'
DB_PASSWORD='DeinSicheresWpUserPasswort'
DB_ROOT_PASSWORD='DeinSicheresDbRootPasswort'

DB_DEFAULT_URL="jdbc:mysql://db:3306/trnydb"
DB_DEFAULT_DRIVER="com.mysql.cj.jdbc.Driver"
DB_DEFAULT_ROOT_PASSWORD="rootpassword"
DB_DEFAULT_USERNAME="playuser"
DB_DEFAULT_PASSWORD="playpassword"

# E-Mail / SMTP Konfiguration
MAIL_HOST="smtp.strato.de"
MAIL_PORT="465"
MAIL_USER="info@turnier-service.org"
MAIL_PASS="DeinSicheresSmtpPasswort"
MAIL_FROM="info@turnier-service.org"
MAIL_FROM_NAME="Turnier Service"
MAIL_ENCRYPTION="ssl"

# WordPress & Play Framework Anbindung
WP_URL='https://rolicht.de'
WP_TITLE=${MY_TITLE}
WP_ADMIN_USER=${MY_ADMIN_NAME}
WP_ADMIN_PASSWORD=${MY_ADMIN_PASS}
WP_ADMIN_EMAIL=${MY_EMAIL}
WP_DB_HOST='db:3306'

PLAY_HTTP_PORT="9500" 
PLAY_HTTP_HOST="backend"
PLAY_HTTP_SECRET_KEY="DeinLangerZufaelligerPlaySecretKey"
PLAY_SERVER_URL="http://backend:9500"
PLAY_PIDFILE_PATH="/dev/null"
PLAY_WP_URL="http://backend:80"
PLAY_WP_CPT="tourney"

# Cloudflare Turnstile Captcha & Google Auth
TURNSTILE_SITEKEY="0x4AAAAAAD7SPzOMwdGfgA5c"
TURNSTILE_SECRET="0x4AAAAAAD7SP4S_6pH0lNBx2GMQ2lXdhbY"
GOOGLE_CLIENT_ID="deine-google-client-id.apps.googleusercontent.com"
```

---

## 5. Verzeichnisse & Dateirechte initialisieren

Führe vor dem ersten Start das Build-/Vorbereitungs-Skript aus, um die erforderlichen Datenordner und Dateirechte für WordPress (`wp_data`), Datenbank (`db_data`) und Logs (`logs`) anzulegen:

```bash
cd ~/server/playdemo/server/docker/prod
./build.sh
```

---

## 6. Produktiv-Container starten

Starte die Produktivumgebung mit dem bereitgestellten `start.sh`-Skript:

```bash
./start.sh
```

Das Skript führt im Hintergrund folgenden Befehl aus:
```bash
docker compose -f docker-compose.yml --env-file .env up -d
```

### Container-Status überprüfen:
```bash
docker ps
```
Es sollten folgende Container laufen:
- `playdemoapp-reverse-proxy-1` (Traefik SSL/TLS Reverse Proxy)
- `wp-db-instance` (MariaDB Datenbank)
- `wp-gmp-instance` (WordPress PHP/GMP Webserver Container)
- `wp-cli-instance` (WP-CLI Dienst)
- `playsrv-instance` (Play Framework Backend Service)

---

## 7. Daten aus der Entwicklungs-Umgebung importieren (Optional)

Wenn ein Datenbestand aus der Entwicklungsumgebung auf das Produktivsystem übertragen werden soll:

1. Auf dem **Entwicklungsrechner** das Migrationsarchiv erstellen:
   ```bash
   cd server/docker/dev
   ./migrate_dev_to_prod.sh
   ```
   *Erzeugt die Datei `dev_migration.zip`.*

2. `dev_migration.zip` auf den **Produktivserver** nach `~/server/playdemo/server/docker/prod/` hochladen (z. B. per `scp` oder `rsync`).

3. Auf dem **Produktivserver** den Import ausführen:
   ```bash
   cd ~/server/playdemo/server/docker/prod
   ./import_to_prod_from_dev.sh
   ```
   *Das Skript entpackt die Dateien nach `wp_data/`, importiert den Datenbank-Dump über WP-CLI, führt automatisch einen URL-Search-Replace auf die Produktiv-Domain (`WP_URL`) durch und leert den WordPress-Cache.*

---

## 8. Verifikation & Log-Überwachung

- **System-Logs einsehen:**
  ```bash
  ./info.sh
  ```
  oder manuell:
  ```bash
  docker compose logs -f --tail=100
  ```

- **HTTPS-Aufruf im Browser:**
  Öffne `https://rolicht.de` (bzw. deine konfigurierte `WP_URL`). Traefik stellt automatisch ein gültiges Let's Encrypt SSL-Zertifikat aus.

---

## 9. Automatische Backups konfigurieren (Cronjob)

Die Produktivumgebung enthält ein automatisches Backup-Skript [`backup.sh`](file:///Users/robert/Projects/Playdemo/server/docker/prod/backup.sh), das Datenbank-Dumps und Dateisicherung auf ein externes `rclone`-Ziel (z. B. Google Drive) überträgt.

1. **`rclone` Remote konfigurieren (falls noch nicht geschehen):**
   ```bash
   rclone config
   ```
   *Erstelle ein Remote namens `gooDrive`.*

2. **Cronjob für tägliche Sicherung anlegen:**
   ```bash
   crontab -e
   ```
   Füge folgende Zeile hinzu (Ausführung täglich um 03:00 Uhr nachts):
   ```cron
   0 3 * * * /home/robert/server/playdemo/server/docker/prod/backup.sh >> /home/robert/server/playdemo/server/docker/prod/logs/backup.log 2>&1
   ```

---

## 10. Zusammenfassung der wichtigsten Befehle

| Aktion | Befehl (in `server/docker/prod/`) |
|---|---|
| Container starten | `./start.sh` |
| Container stoppen | `./stop.sh` |
| Images neu bauen & starten | `./build.sh` |
| Logs & Statistiken einsehen | `./info.sh` |
| Backup manuell ausführen | `./backup.sh` |
| Dev-Migration importieren | `./import_to_prod_from_dev.sh` |
| System zurücksetzen / leeren | `./clean.sh` |


 ┃ /btw könnte man das sauberer trennen?
 ┃
 ┃   Ja, die Trennung lässt sich sowohl innerhalb des bestehenden Repositories als auch durch eine Aufteilung in mehrere Repositories noch deutlich sauberer und professioneller gestalten. Hier sind die
 ┃   bewährten Best Practices dafür:
 ┃   ──────
 ┃   ### 1. Strukturierung im Monorepo (Empfohlen ohne Repo-Aufspaltung)
 ┃
 ┃   Aktuell liegen Docker-Deployments tief verschachtelt unter server/docker/. Das vermischt Applikations-Code (server/) mit Infrastruktur-Code. Eine sauberere Wurzelstruktur wäre:
 ┃ 
 ┃     ├── apps/               (oder server/ & client/)
 ┃     │   ├── server/         # Play Framework Backend (Scala 3)
 ┃     │   └── client/         # Scala.js Frontend
 ┃     ├── wp-plugin/          # WordPress Plugin Source Code
 ┃     ├── infra/              # Infrastruktur & Deployment (statt server/docker)
 ┃     │   ├── dev/            # docker-compose.yml & .env.example (Dev)
 ┃     │   └── prod/           # docker-compose.yml & .env.example (Prod)
 ┃     ├── tests/              # Test-Suites
 ┃   ### 2. Vermeidung von Docker-Duplikaten (DRY mit Compose Overrides)
 ┃
 ┃   Statt zwei fast identische docker-compose.yml-Dateien in dev/ und prod/ zu pflegen, nutzt man den Docker-Standard für Vererbung:
 ┃
 ┃   • docker-compose.yml (Basis): Enthält gemeinsame Services, Netzwerke und Volumes.
 ┃   • docker-compose.override.yml (Dev): Wird lokal automatisch geladen (z. B. Port-Mappings wie 8080, Mocks, Addons).
 ┃   • docker-compose.prod.yml (Prod): Wird für die Produktion eingebunden (z. B. Traefik-Labels, Restart-Policies, Produktions-Container).
 ┃
 ┃   Beispiel-Aufruf in Prod:
 ┃
 ┃     docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env up -d
 ┃     ──────
 ┃   ### 3. Aufteilung in Multi-Repositories (Bei wachsendem Team / Release-Zyklen)
 ┃
 ┃   Falls das WordPress-Plugin oder die Infrastruktur unabhängig vom Backend deployed/versioniert werden soll, empfiehlt sich die Trennung in drei eigene Git-Repositories:
 ┃
 ┃   1. tourney-app (Core Application Repository):
 ┃       • Enthält Play Server, Scala.js Client und das shared-Modul.
 ┃       • Baut die Docker Images (jorolich/playsrv-image) via CI/CD (GitHub Actions) bei Tags/Releases.
 ┃   2. tourney-wp-plugin (WordPress Plugin Repository):
 ┃       • Reines PHP/JS-Plugin für die WordPress-Integration. Kann separat im WordPress-Ökosystem versioniert und gepflegt werden.
 ┃   3. tourney-deploy (Infrastructure & Operations Repository):
 ┃       • Enthält ausschließlich Docker-Compose-Dateien, Server-Setup-Skripte, Backup-Routinen und .env.example-Vorlagen.
 ┃       • Der Produktions-Server klont nur dieses Deployment-Repository.