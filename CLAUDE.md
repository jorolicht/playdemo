# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projekt

- Scala 3 (3.3.7) Playframework Cross Project mit Scala.js Client (Twirl-Templates auch clientseitig) und WordPress-Plugin, in dem der Scala.js Client verwendet wird (Turnier-Service: Turniere, Wettbewerbe, Spieler, Vereine, Stages).
- Kommentiere alle Methoden und Objekte entsprechend den Dokumentierungsrichtlinien für Scala (Scaladoc) und WordPress (PHPDoc).
- `server/` – Playframework Server (JVM)
- `client/` – Scala.js Client mit optionalem `addon`; `addon` bietet eine Console zum Testen in der JavaScript-Console des Browsers (`startConsole()`)
- `shared/` – Code, der sowohl für Server als auch Client übersetzt wird (crossProject, `CrossType.Pure`)
- `wp-plugin/` – WordPress-Plugin `tourney`; Inhalt wird beim Übersetzen nach `server/docker/<env>/wp_data/wp-content/plugins/tourney` kopiert
- `server/docker/dev` – Docker-Setup der Development-Umgebung, `server/docker/prod` – Produktiv-Umgebung
- `test/` – Shell-/curl-Testskripte für die WordPress-API (`test/API`, `test/tourney`, `test/admin`, `test/misc`, `test/payhip`) sowie Playwright E2E-Tests (`test/e2e`)
- `site/content` – statische Seiten, werden nach `wp-plugin/pages` kopiert
- `doc/` – lokale Notizen (nicht im Git, per `.gitignore` ausgeschlossen; u. a. `doc/build.md`: ausführliche Beschreibung aller sbt-Tasks); die Projektdoku steht in `README.md`

## Build & Befehle

sbt startet automatisch im Projekt `server` (`Global / onLoad`).

- `sbt compile` – kompiliert Server **und** löst `scalaJSPipeline` + `syncClientWpFiles` aus (fastLinkJS + fullLinkJS, Kopie von JS/CSS/PHP/pages/data nach `wp-plugin/` und ins Docker-Plugin-Verzeichnis)
- `sbt run` – Play Dev-Server
- `sbt buildMsg` – Nachrichtendateien neu erzeugen (`genMsgFiles` → `convertMessagesToJson`)
- `sbt packageWpPlugin` (Alias `packageWp`) – installierbares ZIP `target/wordpress/tourney-<version>.zip`
- `sbt publishWpPlugin` (Alias `publishWp`, nur mit `APP_FWS=false`) – Plugin-ZIP zusätzlich in den öffentlichen Download-Bereich `server/docker/<env>/downloads/` (`tourney-embed-<version>.zip` + `tourney-embed-latest.zip`), ausgeliefert vom Service `downloads` (nginx) unter `/downloads/`
- `sbt stage` / `sbt dist` – Play-Distribution, wird nach `server/docker/<env>/playsrv/` kopiert
- `sbt dockerBuildImages` / `dockerPushImages` – Images `jorolich/{playsrv,wp-cli,wp-gmp}-image` (linux/amd64)

**Umgebung:** Ziel-Docker-Verzeichnis wird über `APP_ENV` bzw. `-Dapp.env` gewählt; **Default ist `prod`**. Für Entwicklung: `APP_ENV=dev sbt compile`. `APP_VERSION`, `APP_DATE` etc. kommen aus `server/docker/<env>/.env`.

**Plugin-Variante:** `APP_FWS=true|false` (Default `true`). Daraus wird `includes/config.php` mit `TOURNEY_FWS` erzeugt. Bei `false` gibt es keine Plugin-Header/-Footer und kein `pages/`, das ZIP heißt `tourney-embed-<version>.zip`. Die Startseite wird aus `fws`, Homepage (`data-ishome`) und Schreibrecht auf die Seite (`data-canedit`, PHP `tourney_user_can_edit_post`) bestimmt (`Main.runDefaultRouting`). Das Shortcode-Attribut `mode` gibt es nicht mehr; `[tourney page="login|register|verify"]` öffnet eine bestimmte Seite.

**Voraussetzung:** externes Tool `msgConverter` muss im `PATH` liegen (wird von `convertMessagesToJson` aufgerufen).

**Dev-Umgebung starten:** `server/docker/dev/start.sh` (bzw. `stop.sh`) – WordPress, Play-Server (`playsrv`, Port 9500), MySQL via docker compose.

### Tests

Es gibt keine Scala-Unit-Tests. E2E-Tests (Playwright) in `test/e2e` (Konfiguration über `test/e2e/.env`, siehe `.env.example`; Basis-URL `WP_URL`):

```
cd test/e2e
npx playwright test                                  # alle
npx playwright test tests/main_search.spec.ts        # eine Datei
npx playwright test -g "<Testname>" --project=chromium
```

Testkatalog: `test/TestCaseCatalog.md`, Konzept: `test/TestInfo.md`.

## Architektur

- **Einstieg Client:** WordPress-Shortcode `[tourney]` (`wp-plugin/tourney.php`) lädt `js/main.js` als ES-Modul und ruft `startApp(version, mode, logLevel, tourney)` (`client/src/main/scala/Main.scala`). Konfiguration (dataurl, playurl, homeurl, nonce, pageid, slug …) wird aus data-Attributen des Param-Elements gelesen und in `base.Global` abgelegt.
- **UI-Struktur Client:** `pages/`, `comps/`, `dialogs/` mit Twirl-Templates unter `client/src/main/twirl/cviews/`. Pages/Comps/Dialogs sind in Maps registriert (`pages.pagesMap`, `comps.compsMap`, `dialogs.dlgMap`). HTML-Events laufen über die globale Funktion `appEvent`; die Element-ID hat das Format `<Name>_<key>` und wird an `handleEvent` der zugehörigen Komponente weitergeleitet. Seitenwechsel über `appLoadPage(pageName, param)`.
- **Datenhaltung Client:** `services/*DB` (TourneyDB, CompetitionDB, PlayerDB, ClubDB, StageDB) halten den Zustand, synchronisieren debounced über `ComWrapper` (ajax) und schreiben bei `beforeunload` per `flushSync()`. Daten liegen primär in WordPress (REST-Namespace `tourney/v1`, `wp-plugin/includes/api/*.php`, Auth per `X-WP-Nonce`/JWT); der Play-Server (`Global.playUrl`) liefert Hilfsdienste. Schreibende Aufrufe (`*-sync`, `save-aci`, `meta-data` …) verlangen Schreibrecht auf den Post (`tourney_api_can_edit_request_post`); die einzige anonyme Schreiboperation ist die öffentliche Anmeldung `public-register` (`includes/api/player.php`, serverseitig geprüft).
- **Play-Server** (`server/conf/routes`): Maps/Geocoding-Proxy, Messages, ClickTT-Konvertierung, Referee-Eingabe, Matchboard, WebSockets/SSE (Pekko-Actors), WordPress-Proxy (`/wp/post`).
- **Shared:** Domänenmodell (`shared/model`), Turnierformate (`shared/format`: RoundRobin, SingleElimination, SwissSys, Groups), uPickle-Serialisierung. `shared.AppVersion` wird aus `APP_VERSION` generiert.
- **i18n:** Nur die Teildateien `server/conf/messages/{de,en}/*.{de,en}` bearbeiten. `server/conf/messages.{de,en}`, `00_info.*` und `msgs_{de,en}.json` (in `server/public/data`, `wp-plugin/data`, Docker-Plugin-Verzeichnis) sind generiert.
- **Generierte/kopierte Dateien nicht direkt bearbeiten:** `wp-plugin/js`, `wp-plugin/pages` (aus `site/content`), `wp-plugin/data`, alles unter `server/docker/*/wp_data/.../plugins/tourney`.
- **WordPress-Slug-Struktur:** `<club>/jjjj-mm-dd/<id>-tourney`, `<id>-<number>-competition`, `<id>-<number>-round`, `<id>-clubs`, `<id>-players`.

## Arbeitsweise

- Think before coding. State your assumptions out loud. If the request is ambiguous, ask. If a simpler approach exists, push back. Stop when you are confused, name what is unclear, do not just pick one interpretation and run.
- Simplicity first. Write the minimum code that solves the problem. No speculative abstractions. No flexibility nobody asked for. The test: would a senior engineer call this overcomplicated.
- Surgical changes. Touch only what the task requires. Do not improve neighboring code. Do not refactor what is not broken. Every changed line should trace back to the request.
- Goal-driven execution. Turn vague instructions into verifiable targets before writing a line. “Add validation” becomes “write tests for invalid inputs, then make them pass.”
