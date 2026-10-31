=== Tourney ===
Contributors: rolicht
Donate link: https://turnier-service.info
Tags: tournament, table tennis, sports, scalajs, playframework
Requires at least: 6.0
Tested up to: 6.7
Requires PHP: 8.0
Stable tag: 1.0.6
License: GPLv2 or later
License URI: https://www.gnu.org/licenses/gpl-2.0.html

Tourney WordPress Plugin zur Organisation und Durchführung von Tischtennisturnieren und Turniersportarten.

== Description ==

Das Tourney Plugin verbindet eine moderne reaktive Single-Page-Application (Scala.js) mit WordPress und dem Playframework Server. Es ermöglicht die vollständige Verwaltung von Turnieren, Konkurrenzen, Spielern, Vereinen und Spielplänen direkt in WordPress.

Hauptmerkmale:
* Nahtlose Integration als WordPress Custom Post Type (CPT `tourney`) und per Shortcode `[tourney]`.
* Schnelle reaktive Benutzeroberfläche powered by Scala.js und Bootstrap 5.
* Turniersysteme: Gruppenphase, KO-System, Schweizer System (SwissSys) und Round Robin.
* Automatische Auslosung und Setzung nach Ranglisten/TTR-Werten.
* Druck und Anzeige von Schiedsrichterzetteln, Spielberichten und Urkunden.
* Integrierter Schutz gegen Bots über Cloudflare Turnstile.
* Synchronisierte Mehrsprachigkeit (Deutsch und Englisch).

== Installation ==

1. Laden Sie das Plugin in das Verzeichnis `/wp-content/plugins/tourney` hoch.
2. Aktivieren Sie das Plugin über das Menü 'Plugins' in WordPress.
3. Konfigurieren Sie unter den Tourney-Einstellungen die Playframework-Server-URL und optional den Cloudflare Turnstile Sitekey.
4. Fügen Sie den Shortcode `[tourney]` in eine Seite ein oder erstellen Sie einen Beitrag vom Typ 'Turnier' (`tourney`).

== Frequently Asked Questions ==

= Wie wird das Plugin aktualisiert? =
Das Plugin verwendet Semantic Versioning (SemVer) `MAJOR.MINOR.PATCH`. Datenbankaktualisierungen und Schema-Migrationen werden automatisch über `tourney_db_version` beim Aktivieren oder nach einem Plugin-Update ausgeführt.

= Wie funktioniert das Cache-Busting für Scripts und Styles? =
Alle JavaScript- und CSS-Dateien werden dynamisch mit dem Änderungszeitstempel (`filemtime`) oder der aktuellen Plugin-Version versioniert, sodass Browser-Caches nach Updates sofort invalidiert werden.

== Changelog ==

= 1.0.6 =
* Login-Fehler (z. B. falsches Passwort) und fehlgeschlagener Passkey-Login im Fehlerdialog: nur der Fehlertext, Code und CallStack über "Details".
* Website-Variante: Registrierungsseite (Slug "registrierung") auf noindex.

= 1.0.5 =
* Turnier-Info: Wettbewerbsliste als Baum mit Stages und direkt anklickbaren Stage-Reitern.
* Neuer Stage-Reiter „Urkunden“ (Ergebnisansicht mit Urkunden-Button pro Spieler).
* Fehlerdialog mit Details (Code, CallStack) statt Browser-Alert.
* Gültige REST-Nonces nach Login/Logout und Passkey-Registrierung.
* Website-Variante: Startseite und statische Seiten serverseitig im HTML, eigene URLs, englische Fassung unter /en/ (hreflang, Sprachumschalter), SEO-Head-Tags und Sitemap.
* Website-Variante: Social-Media-Links im Footer, anonyme Nutzungsstatistik mit Shortcode [tourney_stats].

= 1.0.4 =
* Zwei Varianten: Website-Version und einbettbares Plugin (tourney-embed) ohne Verwaltungs-, Konfigurator- und Payhip-Funktionen.
* Öffentlicher Download-Bereich für das Plugin unter /downloads/.
* Startansicht abhängig von Schreibrecht auf die Seite; alle schreibenden REST-Aufrufe verlangen Schreibrecht auf den Post.
* Einheitliche Neuanlage von Demo, Turnier und Wettbewerb mit Überschreib-Bestätigung.
* Keine Play-Server-Aufrufe im Plugin-, Lokal- und Demo-Modus.
* Warnung beim Verlassen der Seite, solange Änderungen noch nicht gespeichert sind.
* Ergebnisliste: Die gewählte Urkundenphase wird auch bei KO-Phasen ausgewertet.
* Ergänzte englische Übersetzungen.

= 1.0.3 =
* Einführung des Semantic Versioning (SemVer) MAJOR.MINOR.PATCH Schemas.
* Vollständige Versionssynchronisation zwischen Plugin-Header, readme.txt, tourney_db_version und Scala.js Client.
* Cache-Busting für alle Frontend-Assets (Scripts und Styles) via Filemtime und TOURNEY_VERSION.
* Automatisierte DB-Schema-Migration bei Plugin-Updates.
* Unterstützung für MailCatcher im Docker-Netzwerk und verbesserte E2E-Testunterstützung.

= 1.0.2 =
* Erweiterung um Cloudflare Turnstile CAPTCHA für Benutzerregistrierungen und Formulare.
* Korrektur von Twirl-Templates bei Stage-Layouts und Internationalisierung.

= 1.0.1 =
* Bereitstellung von Shortcode-Optionen für verschiedene Anzeigemodi (`pure`, `home`, `view`).
* Erweiterung der REST-API um WebAuthn/Passkey-Endpunkte.

= 1.0.0 =
* Initiale Veröffentlichung des Tourney WordPress Plugins mit Scala.js Client-Integration.
