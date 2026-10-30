# TurnierService (Tourney)

Webanwendung zur Organisation von **Tischtennis-Turnieren**: Turniere und Wettbewerbe anlegen, Spieler und Vereine verwalten,
Wettbewerbsphasen auslosen, Ergebnisse live erfassen und Urkunden drucken. Die Anwendung läuft als Single-Page-App im Browser
und wird über ein **WordPress-Plugin** ausgeliefert, entweder als eigenständige Website oder eingebettet in eine bestehende
WordPress-Seite.

Technisch ist das Projekt ein **Scala-3-Cross-Projekt**: ein **Play-Framework**-Server (JVM), ein **Scala.js**-Client mit
Twirl-Templates und gemeinsam genutzter Domänencode.

---

## Inhalt

- [Funktionen](#funktionen)
- [Architektur](#architektur)
- [Technologien](#technologien)
- [Verzeichnisstruktur](#verzeichnisstruktur)
- [Voraussetzungen](#voraussetzungen)
- [Konfiguration](#konfiguration)
- [Build und sbt-Tasks](#build-und-sbt-tasks)
- [Entwicklungsumgebung](#entwicklungsumgebung)
- [WordPress-Plugin](#wordpress-plugin)
- [Client-Architektur](#client-architektur)
- [REST-API und Play-Server](#rest-api-und-play-server)
- [Internationalisierung](#internationalisierung)
- [Tests](#tests)
- [Werbevideo](#werbevideo)
- [Release und Deployment](#release-und-deployment)
- [Produktivserver einrichten](#produktivserver-einrichten)
- [Lizenz](#lizenz)

---

## Funktionen

- **Turniere** anlegen und verwalten (Name, Datum, Veranstalter, Sportart), Demo-Turnier zum Ausprobieren
- **Wettbewerbe** (Einzel/Doppel) mit Spielstärke-Grenzen (TTR) und Statusverlauf von der Konfiguration bis zum Abschluss
- **Spieler und Vereine** verwalten, Anmeldung durch Veranstalter oder öffentliche Selbstanmeldung (mit Cloudflare Turnstile)
- **Wettbewerbsphasen**, die sich verketten lassen: Jeder gegen jeden, Gruppen, K.o.-System, Schweizer System
- **Auslosung**, **Ergebniseingabe** mit Tischvergabe und laufender Spielzeit, **Ergebnisse** (Tabellen, K.o.-Baum)
- **Schiedsrichterzettel** mit QR-Code zur Ergebnis-Selbsteingabe per Smartphone
- **Ergebnisliste und Urkunden** auf Basis einer frei wählbaren Urkundenphase
- **Matchboard** (Live-Anzeige) über WebSockets/SSE
- **Import** von Click-TT-Daten
- Speicherung in der **Cloud** (WordPress) oder **lokal** im Browser
- Anmeldung mit Passwort, **Passkeys** (WebAuthn) oder JWT

---

## Architektur

```
 Browser
 ┌──────────────────────────────────────────────┐
 │ Scala.js-Client (main.js, Twirl-Templates)   │
 └───────────┬──────────────────────┬───────────┘
             │ REST tourney/v1      │ Hilfsdienste, WebSocket/SSE
             ▼                      ▼
 ┌───────────────────────┐   ┌──────────────────────────┐
 │ WordPress + Plugin    │   │ Play-Server (/srv)       │
 │ "tourney" (PHP)       │◄──│ Maps-Proxy, Messages,    │
 │ Datenhaltung (Posts,  │   │ Click-TT, Referee-Eingabe,│
 │ Post-Meta)            │   │ Matchboard, WS/SSE        │
 └───────────┬───────────┘   └────────────┬─────────────┘
             ▼                            ▼
 ┌──────────────────────────────────────────────┐
 │ MariaDB                                      │
 └──────────────────────────────────────────────┘
       Traefik (HTTPS) leitet /, /srv und /downloads weiter
```

- **Daten** liegen primär in WordPress (Custom Post Types, Post-Meta). Der Client spricht den REST-Namespace `tourney/v1` an.
- Der **Play-Server** liefert Hilfsdienste und wird nur in der Website-Variante genutzt, nicht im eingebetteten Plugin und
  nicht bei lokalen Turnieren oder dem Demo-Turnier.
- **Shared-Code** (Domänenmodell, Turnierformate, Serialisierung) wird für JVM und JavaScript übersetzt.

---

## Technologien

| Bereich | Technologie |
|---|---|
| Sprache | [Scala 3.3.7](https://www.scala-lang.org/), sbt 1.9.8 |
| Server | [Play Framework 3.0.2](https://www.playframework.com/), Apache Pekko (Streams, WebSockets), Anorm, MySQL Connector, Play Mailer |
| Client | [Scala.js 1.19](https://www.scala-js.org/), scalajs-dom 2.8, [Twirl](https://github.com/playframework/twirl) (auch clientseitig) |
| Gemeinsam | [uPickle 3.3](https://com-lihaoyi.github.io/upickle/) (JSON), [Cats](https://typelevel.org/cats/) (`EitherT`) |
| Oberfläche | [Bootstrap 5.3](https://getbootstrap.com/), [Bootstrap Icons](https://icons.getbootstrap.com/) |
| WordPress | Plugin `tourney` (PHP ≥ 8.0, WordPress ≥ 6.0), Composer |
| Infrastruktur | Docker Compose, Traefik v3 (HTTPS, Let's Encrypt), MariaDB 10.11, nginx (Download-Bereich) |
| Tests | [Playwright](https://playwright.dev/) (E2E), Shell-/curl-Skripte für die REST-API |

Einbindung von Scala.js in Play über [sbt-web-scalajs](https://github.com/vmunier/sbt-web-scalajs) (Vorlage:
[play-scalajs.g8](https://github.com/vmunier/play-scalajs.g8)).

---

## Verzeichnisstruktur

```
.
├── build.sbt                 Build-Definition (Projekte, Tasks, Docker-Targets)
├── project/                  sbt-Plugins
├── server/                   Play-Server (JVM)
│   ├── app/                  Controller, Services, Actors
│   ├── conf/                 routes, application.conf, messages/ (Übersetzungen)
│   └── docker/
│       ├── dev/              Entwicklungsumgebung (docker-compose, Skripte)
│       └── prod/             Produktivumgebung (docker-compose, Skripte, Dockerfiles)
├── client/                   Scala.js-Client
│   └── src/main/
│       ├── scala/            pages/, comps/, dialogs/, services/, base/, addon/
│       └── twirl/cviews/     Twirl-Templates (pages, comps, dialogs)
├── shared/                   Gemeinsamer Code (model/, format/), crossProject CrossType.Pure
├── wp-plugin/                WordPress-Plugin "tourney" (tourney.php, includes/, css/, ...)
├── site/content/             Statische Seiten (werden nach wp-plugin/pages kopiert)
└── test/
    ├── e2e/                  Playwright-Tests und Werbevideo (video/)
    ├── API/, tourney/, admin/, misc/, payhip/   Shell-/curl-Testskripte
    ├── TestCaseCatalog.md    Testkatalog
    └── TestInfo.md           Testkonzept
```

Generierte bzw. kopierte Dateien werden nicht direkt bearbeitet: `wp-plugin/js`, `wp-plugin/pages`, `wp-plugin/data` und alles
unter `server/docker/*/wp_data/.../plugins/tourney`.

---

## Voraussetzungen

| Werkzeug | Zweck |
|---|---|
| JDK 21 | Server und Build |
| sbt 1.9+ | Build |
| Docker mit Docker Compose v2 | Entwicklungs- und Produktivumgebung |
| `msgConverter` im `PATH` | Umwandlung der Übersetzungsdateien in JSON (`convertMessagesToJson`) |
| Node.js 20+ und npm | Playwright-Tests |
| ffmpeg (optional) | Werbevideo |

---

## Konfiguration

Die Umgebungen werden über `server/docker/<env>/.env` konfiguriert (`<env>` = `dev` oder `prod`). Vorlagen liegen daneben als
`.env.example`:

```bash
cp server/docker/dev/.env.example server/docker/dev/.env
# Werte mit CHANGE_ME durch eigene, sichere Werte ersetzen
```

> **Wichtig:** `.env`-Dateien enthalten Passwörter und Schlüssel und werden **nie** eingecheckt. `.gitignore` schließt
> `.env`, `.env.*`, `.env_*` und `*.env` aus; erlaubt sind nur Vorlagen `*.env.example` mit Platzhaltern.

Wichtige Variablen:

| Variable | Bedeutung |
|---|---|
| `MAJOR`, `MINOR`, `PATCH`, `APP_VERSION`, `APP_DATE` | Version und Datum, werden vom Build gelesen |
| `APP_ORGANIZATION`, `APP_MAINTAINER` | Metadaten des Builds |
| `MY_DOMAIN`, `WP_URL` | Domain und WordPress-Adresse |
| `DB_*` | Zugangsdaten MariaDB (WordPress und Play) |
| `WP_ADMIN_*` | WordPress-Administrator bei der Erstinstallation |
| `PLAY_*` | Play-Server (Port, Secret, Mailer) |
| `TURNSTILE_SITEKEY`, `TURNSTILE_SECRET` | Cloudflare Turnstile (Spam-Schutz der Anmeldung) |
| `SOCIAL_FACEBOOK_URL`, `SOCIAL_X_URL`, `SOCIAL_INSTAGRAM_URL` | Social-Media-Buttons im Footer (vollständige URL; leer = Button ausgeblendet) |

Steuerung beim Build:

| Variable | Werte | Bedeutung |
|---|---|---|
| `APP_ENV` (bzw. `-Dapp.env`) | `dev`, `prod` (Standard: `prod`) | Ziel-Docker-Verzeichnis und `.env` |
| `APP_FWS` | `true` (Standard), `false` | Plugin-Variante, siehe [WordPress-Plugin](#wordpress-plugin) |

---

## Build und sbt-Tasks

sbt startet automatisch im Projekt `server`.

| Befehl | Wirkung |
|---|---|
| `sbt compile` | Kompiliert den Server, linkt den Client (`fastLinkJS` + `fullLinkJS`) und kopiert JS/CSS/PHP/Seiten/Daten nach `wp-plugin/` und ins Docker-Plugin-Verzeichnis |
| `sbt run` | Play-Dev-Server |
| `sbt buildMsg` | Übersetzungsdateien neu erzeugen (`genMsgFiles` → `convertMessagesToJson`) |
| `sbt packageWp` | Installierbares Plugin-ZIP `target/wordpress/tourney-<version>.zip` (bei `APP_FWS=false`: `tourney-embed-<version>.zip`) |
| `sbt publishWp` | Nur mit `APP_FWS=false`: Plugin-ZIP in den öffentlichen Download-Bereich `server/docker/<env>/downloads/` (`tourney-embed-<version>.zip` und `tourney-embed-latest.zip`) |
| `sbt stage` / `sbt dist` | Play-Distribution, wird nach `server/docker/<env>/playsrv/` kopiert |
| `sbt dockerBuildImages` / `sbt dockerPushImages` | Images `jorolich/{playsrv,wp-cli,wp-gmp}-image` für linux/amd64 bauen bzw. pushen |

Beispiel für die Entwicklung:

```bash
APP_ENV=dev sbt compile
```

---

## Entwicklungsumgebung

```bash
cd server/docker/dev
./start.sh          # WordPress, Play-Server, MariaDB, Traefik, Mailcatcher, Download-Bereich
./stop.sh
```

| Dienst | Adresse |
|---|---|
| WordPress mit Plugin | https://localhost |
| Play-Server | https://localhost/srv (intern Port 9500) |
| Download-Bereich | https://localhost/downloads/ |
| Mailcatcher (ausgehende Mails) | http://localhost:1080 |

Nach Code-Änderungen genügt `APP_ENV=dev sbt compile`: Der Build kopiert das Plugin direkt in das gemountete
WordPress-Verzeichnis.

Für Tests im Browser bietet der Client eine Konsole: In der JavaScript-Konsole `startConsole()` aufrufen (Addon `addon.Console`).

---

## WordPress-Plugin

Das Plugin `tourney` stellt den Shortcode `[tourney]` bereit. Er lädt `js/main.js` als ES-Modul und startet den Client mit
`startApp(version, mode, logLevel, tourney)`. Konfiguration wie Daten-URL, Play-URL, Nonce und Seiten-ID wird über
data-Attribute übergeben.

```
[tourney]                     Startseite (abhängig von Variante, Startseite und Schreibrecht)
[tourney page="login"]        Anmeldung
[tourney page="register"]     Registrierung
[tourney page="verify"]       E-Mail-Bestätigung
[tourney_stats]               Nutzungsstatistik (nur Administratoren)
```

**Nutzungsstatistik:** Der Client meldet Homepage-Aufrufe (einmal pro Browsersitzung) und Klicks auf „Demo“, „Turnier
anlegen“ und „Wettbewerb starten“ per `navigator.sendBeacon` an `POST tourney/v1/stats-hit?e=home|demo|tourney|comp`
(`includes/api/stats.php`). Gespeichert werden nur Tageszähler in der Tabelle `{prefix}tourney_stats`, ohne IP-Adressen;
Bots und mehr als 30 Meldungen pro IP und Minute werden ignoriert. Auswertung: private Seite (z. B. „Statistik“) mit dem
Shortcode `[tourney_stats]`, Umschaltung pro Tag (30 Tage) / pro Woche (12 ISO-Wochen). Seiten mit diesem Shortcode
werden auch in der Website-Variante im Theme dargestellt.

**Varianten** (Build-Variable `APP_FWS`, erzeugt `includes/config.php` mit `TOURNEY_FWS`):

| | Website (`APP_FWS=true`) | Plugin zum Einbetten (`APP_FWS=false`) |
|---|---|---|
| ZIP | `tourney-<version>.zip` | `tourney-embed-<version>.zip` |
| Seitengerüst | eigenes, ohne WordPress-Header/-Footer | Theme der einbettenden Seite |
| Statische Seiten (`pages/`) | ja | nein |
| Verwaltung, Konfigurator, Payhip | ja | nein |
| Play-Server-Dienste | ja | nein |

Welche Startseite erscheint, hängt von der Variante, davon, ob es die Homepage ist (`data-ishome`), und vom Schreibrecht auf
die Seite ab (`data-canedit`).

**Slug-Struktur** der Turnierdaten in WordPress:

```
<club>/jjjj-mm-dd/<id>-tourney
<club>/jjjj-mm-dd/<id>-<number>-competition
<club>/jjjj-mm-dd/<id>-<number>-round
<club>/jjjj-mm-dd/<id>-clubs
<club>/jjjj-mm-dd/<id>-players
```

Weitere WordPress-Plugins der Website: WP Mail SMTP, Simple Cloudflare Turnstile, JWT Authentication for WP REST API (werden
in der Produktivumgebung automatisch per WP-CLI installiert).

---

## Client-Architektur

- **Seiten, Komponenten, Dialoge** liegen unter `client/src/main/scala/{pages,comps,dialogs}` mit Twirl-Templates unter
  `client/src/main/twirl/cviews/`. Sie sind in Maps registriert (`pages.pagesMap`, `comps.compsMap`, `dialogs.dlgMap`).
- **Ereignisse:** HTML-Elemente rufen die globale Funktion `appEvent` auf. Die Element-ID hat das Format `<Name>_<key>` und
  wird an `handleEvent` der zugehörigen Komponente weitergeleitet.
- **Navigation:** `appLoadPage(pageName, param)`.
- **Datenhaltung:** `services/*DB` (`TourneyDB`, `CompetitionDB`, `PlayerDB`, `ClubDB`, `StageDB`) halten den Zustand und
  synchronisieren verzögert (Debounce) per Ajax (`ComWrapper`). Beim Verlassen der Seite werden ausstehende Änderungen sofort
  geschrieben; solange noch etwas aussteht, fragt der Browser „Seite verlassen?“.
- **Lokaler Modus und Demo:** Daten liegen im `localStorage`, es gibt keine Server-Aufrufe.

---

## REST-API und Play-Server

**WordPress, Namespace `tourney/v1`** (`wp-plugin/includes/api/*.php`), Authentifizierung per `X-WP-Nonce` oder JWT:

| Bereich | Endpunkte (Auswahl) |
|---|---|
| Turnier | `read`, `create`, `delete`, `tourney-sync`, `search`, `organizers`, `meta-data`, `read-aci`, `save-aci` |
| Daten | `competitions`, `players`, `clubs`, `stages`, `rounds` jeweils mit `*-sync` |
| Anmeldung | `public-register` (einzige anonyme Schreiboperation, serverseitig geprüft) |
| Benutzer | `user`, `auth/login`, `auth/logout`, `auth/register`, `auth/verify`, `auth/webauthn/*`, `issue-jwt` |

Schreibende Aufrufe verlangen Schreibrecht auf den jeweiligen Post (`tourney_api_can_edit_request_post`).

**Play-Server** (`server/conf/routes`, unter `/srv`):

| Route | Zweck |
|---|---|
| `/api/map/*` | Karten- und Geocoding-Proxy |
| `/helper/getMessages`, `/helper/getMsg` | Übersetzungen |
| `/helper/clicktt-to-json` | Click-TT-Konvertierung |
| `/referee`, `/referee/submit` | Ergebnis-Selbsteingabe (QR-Code auf dem Schiedsrichterzettel) |
| `/matchboard/*`, `/ws/:slug`, `/helper/send2sse` | Live-Anzeige über WebSocket/SSE |
| `/wp/post` | WordPress-Proxy |

---

## Internationalisierung

Sprachen: Deutsch und Englisch. Bearbeitet werden nur die Teildateien `server/conf/messages/{de,en}/*.{de,en}`. Daraus erzeugt
`sbt buildMsg` die Dateien `server/conf/messages.{de,en}`, `00_info.*` und `msgs_{de,en}.json`. Diese generierten Dateien
nicht direkt bearbeiten.

---

## Tests

Es gibt keine Scala-Unit-Tests. Die Oberfläche wird mit Playwright getestet, die REST-API mit Shell-/curl-Skripten
(`test/API`, `test/tourney`, `test/admin`, `test/misc`, `test/payhip`).

```bash
cd test/e2e
npm install
cp .env.example .env                                   # WP_URL, Testbenutzer
npx playwright test                                     # alle Tests (Chromium, Firefox, WebKit)
npx playwright test tests/main_search.spec.ts           # eine Datei
npx playwright test -g "<Testname>" --project=chromium  # ein Test
```

Testkatalog: `test/TestCaseCatalog.md`, Testkonzept: `test/TestInfo.md`.

---

## Werbevideo

`test/e2e/video/` erzeugt ein etwa zweiminütiges Werbevideo des Demo-Turniers: Playwright nimmt den Ablauf auf, die
Sprecherstimme wird per Text-to-Speech erzeugt, die Musik wird unter der Sprache abgesenkt (Ducking).

```bash
cd test/e2e
node video/make_video.mjs                          # Ergebnis: video/out/promo.mp4
MUSIC=track.mp3 node video/make_video.mjs          # eigene, lizenzfreie Musik
SKIP_RECORD=1 node video/make_video.mjs            # nur neu mischen
TTS_ENGINE=say node video/make_video.mjs           # macOS-Stimme statt Azure
```

Texte, Stimme und Tempo stehen in `video/narration.json`. Für die Azure-Stimme (`de-DE-KatjaNeural`) werden
`AZURE_SPEECH_KEY` und `AZURE_SPEECH_REGION` in `test/e2e/.env` benötigt. Voraussetzungen: ffmpeg, laufende
Entwicklungsumgebung.

---

## Release und Deployment

1. Version in `server/docker/<env>/.env` setzen (`MAJOR`/`MINOR`/`PATCH`, `APP_DATE`).
2. Versionsangaben und Changelogs aktualisieren: `wp-plugin/tourney.php` (Header, `TOURNEY_VERSION`, `TOURNEY_DB_VERSION`),
   `wp-plugin/readme.txt`, `site/content/history*.html`, `site/content/plugin*.html`, News-Zeile in
   `client/src/main/twirl/cviews/pages/MainView/Base.scala.html`, `ARG APP_VERSION` in `server/docker/prod/playsrv/Dockerfile`.
3. Bauen:
   ```bash
   APP_ENV=prod sbt packageWp                 # Website-Plugin
   APP_ENV=prod APP_FWS=false sbt publishWp   # Plugin für den Download-Bereich
   APP_ENV=prod sbt dockerPushImages          # nur bei Änderungen an Server oder Images
   ```
4. Auf den Server übertragen: `server/docker/prod/docker-compose.yml`, `downloads.conf`, `downloads/` und das Plugin-ZIP.
   `downloads.conf` muss vor `docker compose up` vorhanden sein.
5. Auf dem Server: Sicherung (`./backup.sh`), Version in der `.env` anpassen, `./start.sh`, Plugin aktualisieren:
   ```bash
   docker cp tourney-<version>.zip wp-cli-instance:/tmp/
   docker exec wp-cli-instance wp plugin install /tmp/tourney-<version>.zip --force --activate \
       --allow-root --path=/var/www/html
   sudo chown -R 33:33 wp_data/wp-content/plugins/tourney
   ```

Der **Download-Bereich** (`/downloads/`) wird von einem eigenen nginx-Container ausgeliefert und enthält nur die
Plugin-Variante zum Einbetten (`tourney-embed-*.zip`). `tourney-embed-latest.zip` ist die feste Adresse für externe Links.

---

## Produktivserver einrichten

Kurzfassung für einen frischen Ubuntu-Server mit Docker und Docker Compose v2:

1. Domain per A-Record auf den Server zeigen lassen, Firewall öffnen:
   ```bash
   sudo ufw allow 22/tcp && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw enable
   ```
2. Werkzeuge installieren: `sudo apt install -y git curl unzip tar rclone ca-certificates`
3. Repository klonen oder nur `server/docker/prod` übertragen (der Server baut keinen Code, sondern zieht die fertigen Images
   von Docker Hub).
4. In `server/docker/prod` die `.env` aus `.env.example` anlegen und alle `CHANGE_ME`-Werte setzen.
5. `./build.sh` (Verzeichnisse und Rechte anlegen, Container starten), danach `./start.sh` bzw. `./stop.sh`.
6. Traefik holt das TLS-Zertifikat automatisch über Let's Encrypt (`acme.json`). WP-CLI installiert WordPress und die
   benötigten Plugins beim ersten Start.
7. Sicherungen: `./backup.sh` (optional nach Google Drive über rclone, Variablen `GDRIVE_*`, `RETENTION_DAYS`).

---

## Lizenz

Das WordPress-Plugin `tourney` steht unter der **GPLv2 oder später**
([GPL-2.0](https://www.gnu.org/licenses/gpl-2.0.html)).
