=== Tourney ===
Contributors: rolicht
Donate link: https://turnier-service.info
Tags: tournament, table tennis, sports, scalajs, playframework
Requires at least: 6.0
Tested up to: 6.7
Requires PHP: 8.0
Stable tag: 1.5.6
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

= 1.5.6 =
* Durchsagen der Turnierleitung: Eingabe in der Turnierübersicht, Anzeige in „Mein Turnier“ und auf dem Matchboard.
* Matchboard: Kopfleiste mit Logo (Verein bzw. Turnierprogramm), Turniername und Uhr.

= 1.5.5 =
* Tabellen: Direkter Vergleich bei Punktgleichheit (Einzel und Doppel) – bei gleicher Spieldifferenz entscheiden zuerst die Spiele untereinander (Wertungssystem Version 3, gilt für neue Wettbewerbe).

= 1.5.4 =
* Mannschaften: K.-o.-Begegnung im Braunschweiger System wird bei 5:5 über Satz- und Balldifferenz entschieden (bisher blieb sie offen).
* Bezeichnungen für Sportart, Wettbewerbsformate, Spiel/Satz/Ball und Gewinnsätze kommen aus dem Wertungssystem; englische Begriffe jetzt auch in Tabellen und Eingabe.

= 1.5.3 =
* Neue Seite „Wertungssystem“: Satz- und Spielwertung, Kurzschreibweise, Tabellenkriterien, Schweizer System und alle Spielsysteme für Mannschaften; Verweis aus der Hilfe.

= 1.5.2 =
* Fußzeile der Website: Links zum TTC Freising Lerchenfeld und zum Bayerischen Tischtennis-Verband (BTTV).

= 1.5.1 =
* Online-Anmeldung mit Warteliste: Höchstzahl je Wettbewerb, weitere Anmeldungen kommen auf die Warteliste und rücken automatisch nach; Bestätigung per E-Mail, falls angegeben.

= 1.5.0 =
* Turnier kopieren: Vorlage für das nächste Turnier mit Wettbewerben, Spielphasen, Startzeiten, Ort, Kontakt und Homepage-Texten – ohne Spieler und Ergebnisse; alle Termine werden auf das neue Datum verschoben.

= 1.4.9 =
* Demo-Turnier: laufende Spiele zeigen eine realistische Spielzeit.

= 1.4.8 =
* Hilfe & Benutzerhandbuch jetzt auch auf Englisch (/en/help/) mit englischen Screenshots.
* Englische Oberfläche: Turnierseite (Begrüßung, Datum, Kartenhinweis) und Auslosung (Setzen, Feldgröße) übersetzt.

= 1.4.7 =
* Neue Seite „Hilfe“: Benutzerhandbuch mit Screenshots – vom Turnier anlegen über Auslosung und Ergebniseingabe bis zu Urkunden, Online-Anmeldung und „Mein Turnier“.

= 1.4.6 =
* Schweizer System: optimale Auslosung der Folgerunden (Blossom-Matching) – Punktgruppen, keine Wiederholungen, Vereinsschutz und Freilos für die unterste Punktgruppe.

= 1.4.5 =
* Seite „Funktionen“ aktualisiert: Doppeltes K.-o., Mannschaftswettbewerbe, Online-Anmeldung, „Mein Turnier“, mehrere Bearbeiter, Urkunden mit Logo, Offline-App.

= 1.4.4 =
* Seite „Support/Kosten“: Einzelturnier und 5er-Paket direkt über Payhip kaufen.

= 1.4.3 =
* Neues Kostenmodell auf der Seite „Support/Kosten“: Testturnier, Einzelturnier, 5er-Paket, Vereins- und Verbandslizenz.
* Abrechnung: Anlegen reserviert ein Turnier, Löschen ohne Ergebnis gibt es zurück; gekaufte Turniere 2 Jahre gültig.
* Archiv: Cloud-Turniere sind 1 Jahr nach Turnierende schreibgeschützt und bleiben öffentlich sichtbar (nicht mit Lizenz).
* Vereins-/Verbandslizenz im Benutzerprofil und in der Management-Konsole.

= 1.4.2 =
* Eigenes Logo je Turnier: Upload in der Turnier-Administration (Reiter „Homepage“; PNG, JPG oder WebP bis 2 MB, Verkleinerung auf 400 px). Anzeige auf der Turnierseite und auf den Urkunden, in eigenen Urkundenvorlagen über den Platzhalter #Logo.

= 1.4.1 =
* „Mein Turnier“: Spieler wählen auf der Turnierseite ihren Namen (ohne Konto, im Browser gemerkt) und sehen ihre nächsten Spiele mit Gegner und Tisch, ihre Ergebnisse sowie Tabellenplatz bzw. Stand im K.-o.-Baum; Aktualisierung alle 30 Sekunden.

= 1.4.0 =
* Online-Anmeldung auf der Turnierseite: Spieler melden sich mit Name, Vorname, Verein, TTR (optional Jahrgang, E-Mail, WhatsApp) und Captcha zu einem Einzel-Wettbewerb an; Status „registriert“.
* Wettbewerb: Anmeldezeitraum (ab/bis) und „Anmeldung beendet“; Hinweis nach der Anmeldung im Reiter „Homepage“ einstellbar.
* Datenschutz: E-Mail und WhatsApp-Nummer der Spieler nur für Bearbeiter des Turniers abrufbar.
* Mehrbenutzer-Betrieb (Website): Live-Abgleich der Ergebnisse, Zusammenführen je Spiel, Änderungsprotokoll.
* DSGVO-konforme Karte auf der Turnierseite (Website); Export lokaler und Cloud-Turniere über „Meine Turniere“.

= 1.3.1 =
* Statistik: Jeden Morgen um 8 Uhr erhält der Administrator der Website die Nutzungszahlen (gestern, letzte 7 und 30 Tage, Vormonat) per E-Mail.

= 1.3.0 =
* Spielmodus „Doppelte Runde“: Hin- und Rückrunde mit gespiegelten Paarungen, Gesamttabelle aus beiden Runden.
* Spielmodus „Doppeltes K.-o.“: Gewinner- und Verliererrunde mit Finale, optional mit Rückspiel; Platzierung aller Teilnehmer.
* Gekoppelte Abschnitte werden gemeinsam angezeigt, umgeschaltet und gelöscht.

= 1.2.2 =
* ClickTT: Import, Update und Export über das Menü „ClickTT“ in der Turnierübersicht (bisher Administration bzw. „Lade ClickTT-Daten“).
* Start-Buttons (Turnier erstellen, Spielphase, Nachfolger, Runden im Schweizer System) einheitlich mit Start-Symbol.
* „Wettbewerb finalisieren“ auch in der Administration eines Abschnitts.
* Demo-Ergebnisse bei Mannschaften: jeder Spieler nur in einem Doppel.

= 1.2.1 =
* Mannschaftsaufstellung: Jeder Spieler kann nur in einem Doppel eingesetzt werden.
* Auslosung: Der Start-Button nennt die Spielphase („⤳ Start <Name>“).
* Offline-App: Der Sicherungshinweis verweist auf „Meine Turniere → Turnier exportieren in Datei“.

= 1.2.0 =
* Mannschaftswettbewerbe für 2er-, 3er-, 4er- und 6er-Mannschaften mit passenden Spielsystemen und Ergebniseingabe je Begegnung; Mannschaften auch per CSV-Import.
* Wertungssysteme je Sportart, versioniert und je Wettbewerb festgelegt.
* Wettbewerbsabschnitte: Nachfolger direkt aus der Abschnittsliste oder den Ergebnissen starten, Wettbewerb finalisieren mit Urkunden-Abschnitt.
* Auslosung nach dem Start der Spiele gesperrt; gestartete Wettbewerbe nur eingeschränkt bearbeitbar.
* Menü „Meine Turniere“, einheitliche Dialoge, CSV-Import mit Jahrgang, E-Mail und WhatsApp.
* Druck: alle Schiri-Zettel und Stage-Ergebnisse werden vollständig gedruckt.

= 1.1.0 =
* Website-Variante: neue Offline-App (Menü Tools → Offline-App, Seite /app/), als Progressive Web App installierbar; lokale Turniere ohne Internet, Sicherung per JSON-Export.
* Eingebettetes Plugin: keine funktionalen Änderungen.

= 1.0.8 =
* Startseite (Website-Variante): neue Farben für Symbole und Buttons, Fußzeile direkt unter dem Inhalt.
* Kontakt-Seite: E-Mail-Adresse info@turnier-service.org.

= 1.0.7 =
* Korrektur: Die MailCatcher-Umleitung (phpmailer_init) greift nur noch mit TOURNEY_MAILCATCHER (Dev-Umgebung). Seit 1.0.4 wurden alle WordPress-Mails an mailcatcher:1025 umgeleitet.

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
