# Manuelle & Automatisierte Testfall-Checkliste für Playdemo / Turnier-Service

Diese Checkliste dient der qualitätssichernden Abnahme der Benutzeroberfläche und Geschäftslogik der **Playdemo / Turnier-Service Webanwendung**.

Die Testfälle sind nach **UseCases / Seiten** strukturiert und berücksichtigen die drei primären Benutzerrollen:
- 🌐 **Nicht eingeloggt (Anonymer Besucher)**
- 👤 **TourneyAdmin (Turnierverwalter / Turnierorganisator)**
- 👑 **TourneyMaster / WP-Admin (Systemadministrator / Plattformverwalter)**

---

## 1. UseCase: Startseite & Allgemeine Navigation (`MainView`)

### 1.1 Anonymer Besucher (🌐)
- [x] **TC-MV-01 [🌐]:** Aufrufen der Startseite (`/`). Die Seite lädt ohne Fehler, das Logo und die vier Haupt-Container in der Reihenfolge von links nach rechts („Demo“, „Neues Turnier“, „Schnellstart“, „Suchen & Finden“) mit ihren jeweiligen Aktions-Buttons sind sichtbar.
- [x] **TC-MV-01b [🌐]:** Klick auf den Container-Button „Demo“ (`#MainView_BtnDemo`) auf der Startseite bei vorhandenem lokalen Turnier öffnet den Dialog `DlgStartDemo` („Demo lokal starten?“, Warnhinweis „Achtung: das aktuelle lokale Turnier wird gelöscht!“). Nach Bestätigung wird das Demo-Turnier im lokalen Modus gestartet und direkt zu `TourneyInfo` navigiert.
- [x] **TC-MV-01c [🌐]:** Klick auf den Container-Button „Demo“ (`#MainView_BtnDemo`) auf der Startseite ohne vorhandenes lokales Turnier (oder nach dem Löschen eines Wettbewerbs) startet das Demo-Turnier direkt, ohne den Bestätigungsdialog anzuzeigen.
- [x] **TC-MV-02 [🌐]:** Klick auf den Button „Turniersuche“. Die Anwendung wechselt ohne Seiten-Reload zum UseCase `MainSearch`.
- [x] **TC-MV-03 [🌐]:** Klick auf „Ganzes Turnier“ (`#MainView_BtnOption1`) öffnet den Dialog `DlgStartTourney` („Ganzes Turnier starten?“, Warnhinweis „Achtung: das aktuelle lokale Turnier wird gelöscht!“, Buttons „ABBRECHEN“ und „STARTEN“). Bei Klick auf „STARTEN“ wird das Formular zur Erstellung eines neuen Turniers (`TourneyNew`) geöffnet.
- [x] **TC-MV-04 [🌐]:** Aufrufen statischer Seiten über den Footer (z. B. AGB, Datenschutz, Impressum, Kontakt, Preise). Die entsprechenden HTML-Inhalte werden korrekt geladen.
- [x] **TC-FT-02 [🌐]:** Footer enthält externe Links „TTC Freising Lerchenfeld“ (https://ttcfreising.org/) und „BTTV“ (https://www.bttv.de/); beide öffnen in einem neuen Tab (`target="_blank"`, `rel="noopener"`). (`footer_social.spec.ts`)
- [x] **TC-MV-05 [🌐]:** Umschalten der Sprache in der Navigationsleiste (Deutsch / Englisch). Die UI-Texte und statischen Seiten passen sich der gewählten Sprache an.
- [x] **TC-MV-11 [🌐]:** Keine Status-Zeile mehr auf der Startseite: Das letzte Turnier (lokal oder Cloud/Slug) erscheint stattdessen als Eintrag im Navbar-Menü „Meine Turniere“ (siehe 1.4). Existieren keine Speicherschlüssel, zeigt das Menü „Noch keine Turniere verwendet“.
- [x] **TC-MV-11e [🌐]:** Einfacher Wettbewerb (`ident == "SIMPLE"`): Der lokal gespeicherte Wettbewerb erscheint in „Meine Turniere“ als Eintrag „lokal im Browser (<Name>)“; ein Klick öffnet direkt `CompetitionInfo` statt `TourneyInfo`.
- [x] **TC-MV-11f [🌐]:** Automatisches Routing für einfache Wettbewerbe: Direkte Navigation zu `TourneyInfo` bei einem einfachen Wettbewerb (`ident == "SIMPLE"`) wird automatisch abgefangen und zu `CompetitionInfo` weitergeleitet.
- [x] **TC-MV-11g [🌐]:** Resilientes Laden von einfachen Wettbewerben: Beim Laden eines einfachen Wettbewerbs (`ident == "SIMPLE"`) über „Meine Turniere“ werden alle Teildatenbanken vollständig geladen, ohne dass der Dialog „Turnier verlassen“ (`DlgExportTourney`) fälschlicherweise erscheint.

### 1.2 Turnierverwalter (👤)
- [x] **TC-MV-06 [👤]:** Aufrufen der Startseite im angemeldeten Zustand. In der Navigationsleiste werden der Benutzername und der Abmelden-Button angezeigt.
- [x] **TC-MV-07 [👤]:** Klick auf „Guthaben / Vollversion“. Öffnet direkt das Formular zur Erstellung eines neuen Turniers (`TourneyNew`).
- [x] **TC-MV-08 [🌐]:** Klick auf „Wettbewerb starten“ (`#MainView_BtnOption2`) öffnet den Dialog `DlgStartCompetition` („Wettbewerb starten?“, Warnhinweis „Achtung: der aktuelle lokale Wettbewerb bzw das aktuelle lokale Turnier wird gelöscht!“, Buttons „ABBRECHEN“ und „STARTEN“). Bei Klick auf „STARTEN“ wird die Schnellstart-Seite (`CompetitionNew`) zur Wettbewerbserstellung geöffnet (anstelle des `DlgCompetition`-Modals).
- [x] **TC-MV-08b [🌐]:** Schnellstart-Assistent (`CompetitionNew`): Nach Ausfüllen des Formulars und Klick auf „SCHNELLSTART“ wird automatisch das lokale Wrapper-Turnier samt Wettbewerb angelegt und direkt die Ansicht `CompetitionInfo` geöffnet.

### 1.3 Systemadministrator (👑)
- [x] **TC-MV-09 [👑]:** Aufrufen der Startseite als `administrator` oder `tourney_master`. In der Navigationsleiste erscheint zusätzlich der Button „Management“.
- [x] **TC-MV-10 [👑]:** Klick auf den Button „Management“. Die Anwendung wechselt direkt zur Admin-Verwaltungskonsole (`Management`).

### 1.4 Main Navbar: Menü „Meine Turniere“ (🌐 / 👤)
- [x] **TC-NB-01 [🌐]:** Navbar-Layout: Der alte Start-Dropdown-Button, der Menüpunkt „Suchen“, das Badge „Lokaler Modus“, der Demo-Button, der RELOAD-Button und das Menü „Ablage“ sind entfernt. Erstes Menü links ist „Meine Turniere“: oben die zuletzt verwendeten Turniere (höchstens 3, mit Symbol: lokal im Browser, Datei, Cloud; ohne Einträge „Noch keine Turniere verwendet“), nach einem Trennstrich „Öffnen/Archiv“ (Ordnersymbol), „Turnier in Datei exportieren“ (nur mit lokalem Turnier im Browser oder geöffnetem Cloud-Turnier mit Schreibrecht, sonst ausgegraut) und „Turnier exportieren in Cloud“.
- [x] **TC-NB-02 [🌐]:** Demo-Button: Klick auf den „Demo“-Button (im MainView-Container) öffnet den Bestätigungsdialog `DlgStartDemo` („Demo lokal starten?“, Warnhinweis: „Achtung: das aktuelle lokale Turnier wird gelöscht!“) mit 2 Buttons („ABBRECHEN“ und „DEMO STARTEN“). Klick auf „ABBRECHEN“ schließt den Dialog und verbleibt auf der Seite; Klick auf „DEMO STARTEN“ startet das vorkonfigurierte Demo-Turnier direkt im lokalen Modus (`isLocalMode = true`, `isDemoMode = false`), sichert es im lokalen Browserspeicher und navigiert zu `TourneyInfo`.
- [x] **TC-NB-03 [🌐]:** Meine Turniere -> Turnier exportieren in Cloud (nicht angemeldet): Ist der Benutzer nicht angemeldet, ist das Menüelement ausgegraut (`disabled`, `aria-disabled="true"`).
- [x] **TC-NB-04 [👤]:** Meine Turniere -> Turnier exportieren in Cloud (bereits in der Cloud): Ist das aktuelle Turnier bereits in der Cloud (`tourney_is_local_mode = false`), ist das Menüelement ebenfalls ausgegraut (`disabled`).
- [x] **TC-NB-04b [👤]:** Meine Turniere -> Turnier exportieren in Cloud (aktiv): Ist der Benutzer angemeldet und ein lokales Turnier aktiv, ist das Menüelement aktiv und anwählbar.
- [x] **TC-NB-05 [🌐]:** Meine Turniere -> Öffnen/Archiv: Öffnet den Dateiauswahldialog für JSON-Dateien. Eine ausgewählte gültige Turnierdatei wird in den lokalen Modus geladen und direkt in `TourneyInfo` geöffnet. Danach listet „Meine Turniere“ das lokale Turnier und die Datei (Dateiname; eine Kopie liegt im Browser, IndexedDB).
- [x] **TC-NB-06 [🌐]:** Speicherschlüssel `tourney_slug`: Wird synchron zu `tourney_is_local_mode` gepflegt – bei `tourney_is_local_mode = true` ist `tourney_slug = "local"`, bei `tourney_is_local_mode = false` entspricht `tourney_slug` dem tatsächlichen Turnier-Slug `<slug>`.
- [x] **TC-NB-07 [🌐]:** RELOAD-Button entfernt / Eintrag „lokal im Browser (<Name>)“: Ein Klick lädt das im Browserspeicher (`localStorage` / `App.demo_tourney`) abgelegte lokale Turnier und öffnet direkt `TourneyInfo`. Der separate RELOAD-Button ist nicht mehr in der Navbar vorhanden.
- [x] **TC-NB-08 [🌐]:** Ohne lokal gespeichertes Turnier zeigt „Meine Turniere“ keinen Eintrag „lokal im Browser“.
- [x] **TC-NB-09 [🌐]:** Cloud-Eintrag „in der Cloud (<Name>)“ ohne Login: Ein Klick zeigt einen Hinweis („Für das Laden dieses Turniers ist eine Anmeldung erforderlich. Bitte melden Sie sich an.“) und leitet zu `UserLogin` weiter.
- [x] **TC-NB-10 [👤]:** Ohne gespeicherten `tourney_slug` (und ohne zuvor geöffnetes Cloud-Turnier) zeigt „Meine Turniere“ keinen Cloud-Eintrag.
- [x] **TC-NB-11 [👤]:** Cloud-Eintrag ohne Zugriffsrechte: Ist der angemeldete Benutzer weder Administrator noch Veranstalter des Turniers, erscheint ein Berechtigungsfehler („Sie haben keine Berechtigung für dieses Turnier.“).
- [x] **TC-NB-12 [👤]:** Meine Turniere -> Turnier exportieren in Cloud (Ausführung): Klick auf die aktive Option lädt das lokale Turnier in die Cloud hoch, synchronisiert Vereine, Spieler, Wettbewerbe und Abschnitte, entfernt das lokale Duplikat und schaltet in den Cloud-Modus um.
- [x] **TC-NB-13 [👤]:** Meine Turniere -> Turnier in Datei exportieren mit lokalem Turnier im Browser und geöffnetem Cloud-Turnier: Ein Dialog fragt „Welches Turnier soll in eine Datei gespeichert werden?“ (Lokales Turnier „…“ / Cloud-Turnier „…“); die heruntergeladene Datei enthält das gewählte Turnier, das Cloud-Turnier bleibt geöffnet.
- [x] **TC-NB-14 [👤]:** Nur Cloud-Turnier geöffnet (kein lokales Turnier): Der Export öffnet direkt den Dialog mit dem Dateinamen, ohne Auswahl.
- [x] **TC-TA-IE-01 [👤]:** Die Turnier-Administration der Website hat keinen Reiter „Import / Export“ mehr; Aufruf mit `IMPEXP` öffnet „Urkunden Konfiguration“. Im Embed-Plugin (ohne Menü „Meine Turniere“) bleibt der Reiter mit Export (Dateinamen-Dialog) und Import.

---

## 2. UseCase: Turniersuche & Ergebnissuche (`MainSearch`)

### 2.1 Anonymer Besucher (🌐)
- [x] **TC-MS-01 [🌐]:** Aufrufen von `#MainSearch`. Die Eingabefelder für Titel, Veranstalter und Datum sowie die Ergebnistabelle werden angezeigt.
- [x] **TC-MS-02 [🌐]:** Eingabe im Feld „Turniername / Titel“. Nach der Debounce-Verzögerung (400ms) aktualisiert sich die Ergebnistabelle dynamisch.
- [x] **TC-MS-03 [🌐]:** Eingabe im Feld „Veranstalter / Verein“. Die Tabelle filtert nach dem eingegebenen Veranstalternamen.
- [x] **TC-MS-04 [🌐]:** Auswahl eines Datums im Datumsfilter. Es werden nur Turniere angezeigt, deren Startdatum ab dem gewählten Datum liegt.
- [x] **TC-MS-05 [🌐]:** Umschalten des Radiobutton-Filters (Alle / Nur Turniere / Nur Wettbewerbe). Die Ergebnisliste filtert den Typ entsprechend.
- [x] **TC-MS-06 [🌐]:** Klick auf den Spaltenheader „Datum“. Die Ergebnissortierung wechselt zwischen aufsteigend und absteigend (überprüfbar am Sortier-Icon).
- [x] **TC-MS-07 [🌐]:** Klick auf ein Turnier-Ergebnis als anonymer Nutzer. Die Anwendung leitet auf die öffentliche Turnier-Startseite `TourneyWelcome` weiter (keine Bearbeiten-Buttons sichtbar).
- [x] **TC-MS-08 [🌐]:** Klick auf ein Wettbewerbs-Ergebnis als anonymer Nutzer. Die Anwendung leitet auf die öffentliche Wettbewerbs-Seite `CompetitionWelcome` weiter.

### 2.2 Turnierverwalter (👤)
- [x] **TC-MS-09 [👤]:** Klick auf ein Suchergebnis eines Turniers, bei dem der Nutzer der eingetragene `TourneyAdmin` ist. Weiterleitung erfolgt auf `TourneyInfo` mit vollen Verwaltungsrechten.
- [x] **TC-MS-10 [👤]:** Klick auf ein Suchergebnis eines fremden Turniers. Weiterleitung erfolgt wie beim anonymen Nutzer auf `TourneyWelcome`.

### 2.3 Systemadministrator (👑)
- [x] **TC-MS-11 [👑]:** Klick auf ein beliebiges Turnier- oder Wettbewerbsergebnis als `administrator` / `tourney_master`. Unabhängig vom Veranstalter erfolgt die Weiterleitung auf `TourneyInfo` bzw. `CompetitionInfo` im Verwaltungsmodus.

---

## 3. UseCase: Öffentliche Turnier-Startseite (`TourneyWelcome`)

### 3.1 Anonymer Besucher (🌐)
- [x] **TC-TW-01 [🌐]:** Aufrufen von `TourneyWelcome`. Turniername, Veranstalter, Austragungsort, Datum und Wettbewerbsübersicht werden angezeigt.
- [x] **TC-TW-02 [🌐]:** DSGVO-konforme Karte (`/srv/api/map/static`): Der Play-Server lädt Kacheln und Adresssuche selbst und liefert ein PNG 640×320 mit dem Ort in der Mitte, Marker und „© OpenStreetMap-Mitwirkende“; für eine unbekannte Adresse 404.
- [x] **TC-TW-03 [🌐]:** Klick auf den Button „iCal Kalendereintrag (.ics)“. Eine iCalendar-Datei wird zum Download bereitgestellt.
- [x] **TC-TW-04 [🌐]:** Klick auf „Online-Anmeldung“ bei einem zukünftigen Turnier/Wettbewerb. Der Dialog zur öffentlichen Spieler-Anmeldung (`DlgPublicRegistration`) öffnet sich.
- [x] **TC-TW-05 [🌐]:** Klick auf eine Wettbewerbszeile. Die Anwendung wechselt zur öffentlichen Detailansicht `CompetitionWelcome`.
- [x] **TC-TW-07 [🌐]:** Cloud-Turnier mit bekannter Adresse: Unter „Adresse & Anfahrt“ steht die Karte vollständig (Hinweis „© OpenStreetMap-Mitwirkende“ sichtbar); ein Klick öffnet die Adresse bei OpenStreetMap.
- [x] **TC-TW-08 [🌐]:** Unbekannte Adresse bzw. weder PLZ noch Ort: Es wird keine Karte angezeigt; Adresse und die Links OpenStreetMap/Google Maps bleiben.

### 3.2 Turnierverwalter & Admin (👤 / 👑)
- [x] **TC-TW-06 [👤/👑]:** In der Kopfzeile wird zusätzlich ein Button „Zur Turnierverwaltung“ angezeigt, der direkt zur Admin-Ansicht `TourneyInfo` führt.

---

## 4. UseCase: Öffentliche Wettbewerbs-Startseite (`CompetitionWelcome`)

### 4.1 Anonymer Besucher (🌐)
- [x] **TC-CW-01 [🌐]:** Aufrufen von `CompetitionWelcome` mit einer Wettbewerbs-ID. Name, Sportart, Modus, TTR-Bereich und Status werden angezeigt.
- [x] **TC-CW-02 [🌐]:** Überprüfung der Abschnittstabelle. Laufende und beendete Wettbewerbsabschnitte werden gelistet.
- [x] **TC-CW-03 [🌐]:** Klick auf „Ergebnisse / Runden anzeigen“. Die Ergebnisse des jeweiligen Abschnitts werden schreibgeschützt geöffnet (`StageAdmin` im View-Modus).
- [x] **TC-CW-04 [🌐]:** Überprüfung der Teilnehmerliste. Namen, Vereine, TTR-Werte und Jahrgänge werden angezeigt. Klick auf Spaltenheader sortiert die Liste.
- [x] **TC-CW-05 [🌐]:** Klick auf den Button „Turnier-Übersicht“. Führt zurück zu `TourneyWelcome`.

---

## 5. UseCase: Turnier-Verwaltung (`TourneyInfo` & `TourneyAdmin`)

### 5.1 Turnierverwalter (👤)
- [x] **TC-TI-01 [👤]:** Aufrufen von `TourneyInfo`. Die Übersicht zeigt alle zugeordneten Wettbewerbe, Gesamt-Teilnehmerzahlen und den Turnierstatus.
- [x] **TC-TI-02 [👤]:** Klick auf „Wettbewerb hinzufügen“. Der Assistent zur Erstellung eines neuen Wettbewerbs (`CompetitionNew`) öffnet sich.
- [x] **TC-TI-03 [👤]:** Klick auf „Turnierstammdaten bearbeiten“. Wechselt zu `TourneyAdmin`, wo Name, Ort, Datum, Veranstalter und Zusatzfelder angepasst werden können.
- [x] **TC-TI-04 [👤]:** Speichern der Stammdaten in `TourneyAdmin`. Die Änderungen werden übernommen und in `TourneyInfo` sofort reflektiert.
- [x] **TC-TI-05 [👤]:** Klick auf „PDF-Stammblatt drucken“. Ein PDF mit den vollständigen Turnierdaten wird im Browser generiert.
- [x] **TC-TI-06 [👤]:** Klick auf „Turnier beenden / aktivieren“. Der Status des Turniers schaltet zwischen Aktiv und Beendet um.
- [x] **TC-TI-07 [👤]:** Klick auf „Turnier löschen“. Nach Bestätigung im Sicherheitsdialog wird das Turnier gelöscht.
- [x] **TC-TI-09 [👤]:** Anlegen eines Turniers in der Vergangenheit (`TourneyNew`): Ein Startdatum in der Vergangenheit ist nicht erlaubt und wird mit der Fehlermeldung „Das Startdatum darf nicht in der Vergangenheit liegen.“ abgewiesen.
- [x] **TC-TI-10 [👤]:** Anlegen eines Turniers in der Zukunft mit 5 Tagen Laufzeit und Anlegen von 3 Wettbewerben im Turnierzeitraum. Überprüfung der Datumsvalidierung: Wettbewerbe außerhalb des Turnierzeitraums (vor Beginn oder nach Turnierende) werden mit dem Fehlerhinweis „Das Datum des Wettbewerbs muss im Zeitraum des Turniers liegen.“ abgewiesen.
- [x] **TC-TI-11 [👤]:** Lokaler Modus: Nach Neuanlage eines Turniers in `TourneyNew` sind auf der Zielseite `TourneyInfo` alle Aktionsschaltflächen („Bearbeiten“, „Löschen“ sowie „Wettbewerb hinzufügen“) aktiv und voll bedienbar. Bei der Wettbewerbserstellung wird zudem sichergestellt, dass keine Duplikate mit demselben Namen angelegt werden können (wird mit Fehlermeldung „Ein Wettbewerb mit diesem Namen existiert bereits. Bitte wählen Sie einen anderen Namen.“ abgewiesen).
- [x] **TC-TI-12 [👤]:** Lokaler Modus – Export über Menü: Klick auf „Lokal-Optionen“ -> „Turnier exportieren (JSON)“ öffnet den Dialog `DlgExportTourney` mit vorausgefülltem Standarddateinamen `tourney_<name>_<datum>_export.json`. Validierung verhindert leere Dateinamen. Nach Bestätigung wird die JSON-Datei heruntergeladen und der Nutzer verbleibt auf der Seite.
- [x] **TC-TI-13 [👤]:** Lokaler Modus – Turnier verlassen: Klick auf „Speichern/Verlassen“ (`BtnSaveLeave`) speichert/synchronisiert das Turnier und zeigt die Meldung „Speichern & Verlassen“ mit dem Speicherort („Speicherort: "lokal" im Browser“, Cloud-Slug bzw. WordPress-Seiten-ID) und nur einem OK-Button; OK führt ohne JSON-Dateidownload zur Startseite. Verlassen der Turnierseite auf anderem Weg (Navbar-Logo, Menü wie „Funktionen“, Footer-Links wie „Impressum“) zeigt keinen Dialog mehr: Das Turnier wird automatisch gespeichert/synchronisiert (lokal bzw. Cloud), geschlossen und die Zielseite geöffnet; über „Meine Turniere“ lässt es sich wieder öffnen (TC-EXP-03).
- [x] **TC-TI-14 [👤]:** Lokaler Modus – Überschreiben-Prüfung beim Export: Existiert bereits eine Datei mit demselben Namen (aus vorherigem Export oder Import), schließt der Klick auf Exportieren nicht sofort, sondern blendet eine Warnung ein („Eine Datei mit diesem Namen existiert bereits. Möchten Sie die vorhandene Datei wirklich überschreiben?“). Der Button wechselt zu „Überschreiben“. Erst nach Bestätigung wird die Datei überschrieben und heruntergeladen. Wird der Dateiname geändert, verschwindet die Warnung automatisch.
- [x] **TC-TI-15 [👤]:** `TourneyNew` – Button „ERSTELLEN“ & Auswahldialog `DlgTourneyNewMode`: Beim Anlegen eines Turniers lautet der Submit-Button „ERSTELLEN“. Klick öffnet den Auswahldialog zur Wahl zwischen lokalem Turnier (`TourneyTargetMode.Local`) und Cloud-Turnier (`TourneyTargetMode.Cloud`). Für nicht angemeldete Benutzer ist die Cloud-Option ausgegraut (`disabled`) mit dem Hinweis „(Anmeldung erforderlich)“.
- [x] **TC-TI-16 [👤]:** `TourneyNew` – Bearbeiten-Modus (`edit`): Beim Bearbeiten eines bestehenden Turniers lautet der Submit-Button „Turnier speichern“ und speichert die Änderungen direkt ohne Anzeige des Modus-Auswahldialogs.

### 5.2 Anonymer Besucher (🌐)
- [x] **TC-TI-08 [🌐]:** Versuch, `TourneyInfo` oder `TourneyAdmin` als unbefugter/anonymer Nutzer direkt per URL aufzurufen. Die Anwendung leitet automatisch auf `TourneyWelcome` um.

---

## 6. UseCase: Wettbewerbs-Verwaltung (`CompetitionInfo`)

### 6.1 Turnierverwalter (👤)
- [x] **TC-CI-01 [👤]:** Aufrufen von `CompetitionInfo`. Zeigt Wettbewerbs-Details, Abschnittsübersicht und die Teilnehmer-Akkordeon-Tabelle.
- [x] **TC-CI-02 [👤]:** Klick auf „Wettbewerbsabschnitt starten“. Öffnet den Auslosungs-Assistenten (`StageAdmin` / `StageDraw`).
- [x] **TC-CI-03 [👤]:** Klick auf „Wettbewerb bearbeiten“. Erlaubt die Anpassung von TTR-Limits, Altersklassen und Spieltischen.
- [x] **TC-CI-04 [👤]:** Auswahl der Urkunden-Radiobuttons für einen Abschnitt. Markiert den Abschnitt als maßgeblich für den Urkundendruck.
- [x] **TC-CI-05 [👤]:** Klick auf „Wettbewerb beenden“. Der Status wechselt auf Beendet und Bearbeitungsaktionen werden gesperrt.
- [x] **TC-CI-06 [👤]:** Eigenständigen Wettbewerb (`ident == "SIMPLE"`) löschen – Bestätigungsdialog Abbrechen: Klick auf den Löschen-Button öffnet einen Sicherheits-Bestätigungsdialog (`DlgMsgbox`). Klick auf „Nein“ schließt den Dialog und behält den Wettbewerb sowie alle Daten im Speicher unverändert bei.
- [x] **TC-CI-07 [👤]:** Eigenständigen Wettbewerb (`ident == "SIMPLE"`) im lokalen Modus löschen: Klick auf „Löschen“ und Bestätigen mit „Ja“ löscht den lokalen Speicher (`App.demo_tourney`, `App.demo_comps` etc.) sowie sämtliche Browser-Schlüssel (`tourney_is_local_mode`, `tourney_slug`, `tourney_name`, `tourney_ident`, `tourney_last_*`). Navigiert zur Startseite (`MainView`); „Meine Turniere“ zeigt keinen Eintrag „lokal im Browser“ mehr.
- [x] **TC-CI-08 [👤]:** Eigenständigen Wettbewerb (`ident == "SIMPLE"`) im Cloud-Modus löschen: Klick auf „Löschen“ und Bestätigen mit „Ja“ sendet einen `DELETE`-Request an den Server (`/wp-json/tourney/v1/delete`), bereinigt sämtliche Browser-Speicherschlüssel und und navigiert zur Startseite (`MainView`).
- [x] **TC-CI-09 [👤]:** Nach dem Löschen eines Wettbewerbs (`ident == "SIMPLE"`) startet ein nachfolgender Klick auf „Demo“ direkt, ohne dass die Warnmeldung „Achtung: das aktuelle lokale Turnier wird gelöscht!“ erscheint.
- [x] **TC-CI-10 [👤]:** In der Abschnittsliste steht vor „Löschen“ der Button „Nachfolger starten“ (Symbol Staffelübergabe). Er ist deaktiviert, solange im Abschnitt kein Ergebnis eingegeben ist.
- [x] **TC-CI-11 [👤]:** „Nachfolger starten“ bei unvollständigen Ergebnissen: Hinweisdialog mit „ABBRUCH“ und „WEITER“. „ABBRUCH“ legt nichts an, „WEITER“ öffnet „Neuen Wettbewerbsabschnitt starten“ mit dem Abschnitt als Vorgänger.
- [x] **TC-CI-12 [👤]:** „Nachfolger starten“ bei vollständigen Ergebnissen öffnet ohne Rückfrage „Neuen Wettbewerbsabschnitt starten“ mit dem Abschnitt als Vorgänger.
- [x] **TC-CI-13 [👤]:** Bei Mannschaftswettbewerben heißt das Teilnehmer-Accordion in `CompetitionInfo` „Mannschaftsliste“, die Spalten „Mannschaftsname“ und „Ø TTR“; in der Anmeldung (`PlayerRegistration`) heißen sie „Mannschaft“ und „Ø TTR“.
- [x] **TC-CI-14 [👤]:** Bei Einzelwettbewerben bleiben „Teilnehmerliste“, „Name, Vorname“ und „TTR“.
- [x] **TC-CI-15 [👤]:** Im Dialog „Wettbewerb bearbeiten“ sind Kategorie und Wettbewerbstyp immer gesperrt (auch ohne Wettbewerbsabschnitt), mit Hinweis; Name, Beginn und TTR bleiben änderbar.
- [x] **TC-CI-16 [👤]:** „Wettbewerb bearbeiten“: Ein Beginn außerhalb des Turnierzeitraums wird mit Fehlermeldung abgelehnt, der Dialog bleibt offen.
- [x] **TC-CI-17 [👤]:** „Wettbewerb bearbeiten“: Umbenennen auf den Namen eines anderen Wettbewerbs (Groß-/Kleinschreibung egal) wird abgelehnt; ein neuer, freier Name wird übernommen.
- [x] **TC-CI-18 [👤]:** Hat ein Abschnitt bereits einen Nachfolger (ein anderer Abschnitt verweist per Vorgänger auf ihn), erscheint in der Abschnittsliste kein Button „Nachfolger starten“.

---

## 7. UseCase: Teilnehmerverwaltung & Doppelpaarung (`PlayerList` & `PlayerRegistration`)

### 7.1 Turnierverwalter (👤)
- [x] **TC-PL-01 [👤]:** Aufrufen von `PlayerList`. Alle angemeldeten Spieler des Wettbewerbs/Turniers werden gelistet.
- [x] **TC-PL-02 [👤]:** Manuelles Hinzufügen eines Einzelspielers („Spieler hinzufügen“). Nach Eingabe von Name, Verein, TTR und Geburtsjahr erscheint der Spieler in der Liste.
- [x] **TC-PL-03 [👤]:** Importieren von Spielern aus Click-TT / XML / CSV. Die Daten werden korrekt geparst und der Teilnehmerliste hinzugefügt.
- [x] **TC-PL-04 [👤]:** Erstellen von Doppel-Paarungen (in Doppel-/Mixed-Wettbewerben). Zwei Einzelspieler werden zu einem Doppel zusammengefügt.
- [x] **TC-PL-05 [👤]:** Auslosen oder Manuelles Auflösen von Doppelpaarungen.
- [x] **TC-PL-06 [👤]:** Deaktivieren / Löschen eines Teilnehmers. Deaktivierte Spieler werden bei der Phasenauslosung ausgeschlossen.

### 7.2 Anonymer Besucher (🌐)
- [x] **TC-PL-07 [🌐]:** Ausfüllen des öffentlichen Anmeldeformulars (`DlgPublicRegistration`). Nach Absenden erhält der Turnierverwalter die Anmeldung und der Spieler wird registriert.

---

## 8. UseCase: Abschnitts-Verwaltung & Auslosung (`StageAdmin` & `StageDraw`)

### 8.1 Turnierverwalter (👤)
- [x] **TC-SA-01 [👤]:** Aufrufen von `StageAdmin` für einen neuen Abschnitt. Wählen des Spielsystems (z. B. Schweizer System, Gruppenphase, KO-System, Jeder-gegen-Jeden).
- [x] **TC-SA-02 [👤]:** Konfigurieren von Gruppenanzahl, Qualifikanten pro Gruppe und Gewinnsätzen (z. B. Best-of-5).
- [x] **TC-SA-03 [👤]:** Ausführen der Auslosung (`StageDraw`). Die Spieler werden gemäß TTR-Setzung oder Zufall auf Gruppen/KO-Pfade verteilt.
- [x] **TC-SA-04 [👤]:** Manuelles Tauschen von Spielern in Gruppen/Runden vor Freigabe des Abschnitts.
- [x] **TC-SA-05 [👤]:** Freigabe des Abschnitts („Abschnitt starten“). Der Spielplan und die Ergebniseingabe werden aktiviert.
- [x] **TC-SA-06 [👤]:** Nachfolgender Abschnitt mit vorangegangenem Rundenturnier (`RoundRobin`): Filterung nach „Obere Hälfte“, „Untere Hälfte“ und „Alle“ sowie Anzeige der Vorrundenplatzierung in der Spalte `prev_stage`.
- [x] **TC-SA-07 [👤]:** In der Administration (`StageAdmin`) steht unter den vier roten Buttons zusätzlich „Wettbewerb finalisieren“ (wie in `StageResult`): nach Rückfrage wird der Wettbewerb finalisiert und die Wettbewerbsübersicht angezeigt; danach ist der Button gesperrt.
- [x] **TC-SD-10 [👤]:** `StageDraw` bei laufenden Spielen (K.-o.): Hinweis „Auslosung ist abgeschlossen“, kein „SETZEN“ und keine Setzfelder, Start deaktiviert, Klick auf Spieler tauscht nicht.
- [x] **TC-SD-11 [👤]:** `StageDraw` bei beendetem Abschnitt (Jeder-gegen-Jeden): Hinweis, Klick auf Spieler tauscht nicht. (Ausnahme Schweizer System: Auslosung der Folgerunde im Status beendet bleibt möglich.)
- [x] **TC-SD-12 [👤]:** `StageDraw` im Status Auslosung: kein Sperrhinweis, Spieler tauschen weiterhin möglich.
- [x] **TC-SD-13 [👤]:** `StageDraw`: Der Start-Button heißt „Start <Stagename>“, mit Symbol `bi-play-circle` in der Farbe `btn-mv-tourney` (#4A607A).

---

## 9. UseCase: Ergebniseingabe & Spielplan (`StageInput` & `StageResult`)

### 9.1 Turnierverwalter (👤)
- [x] **TC-SI-01 [👤]:** Aufrufen der Ergebniseingabe `StageInput`. Die Ansetzungen der aktuellen Runde / Gruppen werden angezeigt.
- [x] **TC-SI-02 [👤]:** Eingabe von Satzergebnissen (z. B. `11:9, 9:11, 11:8, 11:6`). Das Spielergebnis wird automatisch berechnet (3:1) und der Sieger markiert.
- [x] **TC-SI-03 [👤]:** Zuweisung eines Spieltisches (z. B. „Tisch 4“). Der Tischstatus aktualisiert sich.
- [x] **TC-SI-04 [👤]:** Validierung ungültiger Eingaben (z. B. unvollständige Sätze). Die Anwendung zeigt eine Fehlermeldung und verhindert das Speichern.
- [x] **TC-SI-05 [👤]:** Aufrufen von `StageResult`. Die Gruppentabellen (Spielegewinn, Satzverhältnis, Bälle, Punkte) aktualisieren sich in Echtzeit.
- [x] **TC-SI-07 [👤]:** Im Demo-Modus erscheint in der Kopfzeile von `StageInput` (Card-Title `<h5>`) der Button „DEMO-Ergebnisse“. Ein Klick darauf generiert automatische Zufallsergebnisse für alle ungespielten Begegnungen (inkl. automatischer Siegerpropagation in Folge-K.o.-Runden) und deaktiviert den Button nach Abschluss des Abschnitts.
- [x] **TC-SI-08 [👤]:** Im KO-System (`SingleElimination`) wird für Spiele, bei denen die Teilnehmer noch nicht feststehen, der Button für den Schiedsrichterzettel angezeigt. Auf dem erzeugten Zettel bleiben die Spielernamen leer für manuelle Eintragungen. Bei Spielen mit Freilos wird kein Schiedsrichterzettel-Button angezeigt.
- [x] **TC-SI-09 [👤]:** Im Gruppen-Spielsystem (`Groups`) öffnet der Schiedsrichterzettel-Button einer Spielzeile in `StageInput` die Seite `StageScoreSheet` ausschließlich mit dem ausgewählten Spiel (genau 1 Zettel in der Vorschau, passender Titel und Einzelzettel-Druckbutton) statt aller Zettel der Gruppe oder Runde.
- [x] **TC-SI-10 [👤]:** In `StageResult` ist „Start Nachfolger“ (Jeder-gegen-Jeden, Gruppen, K.-o.-Runde) deaktiviert, solange im Abschnitt kein Ergebnis eingegeben ist (Freilose zählen nicht).
- [x] **TC-SI-11 [👤]:** Sind nicht alle Ergebnisse eingegeben, zeigt ein Klick auf „Start Nachfolger“ nur einen Hinweisdialog (eingegebene/alle Spiele); es wird kein neuer Abschnitt angelegt.
- [x] **TC-SI-12 [🌐]:** Im Demo-Turnier zeigt ein laufendes Spiel eine realistische Spielzeit von wenigen Minuten (die Startzeit laufender Spiele wird beim Laden der Demo angepasst).

### 9.2 Anonymer Besucher (🌐)
- [x] **TC-SI-06 [🌐]:** Aufrufen von `StageResult` im öffentlichen Modus. Ergebnisse und Tabellenstände sind sichtbar, die Ergebniseingabefelder sind jedoch deaktiviert.

---

## 10. UseCase: Druckausgabe (Spielzettel & Urkunden) (`StageScoreSheet` & `Certificate`)

### 10.1 Turnierverwalter (👤)
- [ ] **TC-PR-01 [👤]:** Aufrufen von `StageScoreSheet`. Auswählen von Runden/Spielen zum Drucken von Schiedsrichter-Zetteln.
- [ ] **TC-PR-02 [👤]:** Klick auf „Drucken“. Die Browser-Druckansicht öffnet sich mit optimiertem Layout (keine Navigationsleisten, saubere Formularlinien).
- [ ] **TC-PR-03 [👤]:** Aufrufen von `Certificate` nach Abschluss eines Wettbewerbs.
- [ ] **TC-PR-04 [👤]:** Auswählen einer Urkunden-Vorlage (Layout, Hintergrundbild, Schriftart, Platzierungen 1-4).
- [ ] **TC-PR-05 [👤]:** Generieren der PDF-Urkunden für die Erstplatzierten. Die Namen, Vereine und Wettbewerbstitel werden korrekt eingesetzt.
- [x] **TC-PR-06 [👤]:** „Runde drucken (4 pro Seite)“ in `StageScoreSheet` druckt alle Schiedsrichterzettel der Vorschau (4 pro Seite), nicht nur die erste Seite, und erzeugt keine leere Seite am Ende. Der Druckbutton ist in Hero-Blau. Nur in Chromium (`page.pdf()`).

---

## 11. UseCase: Authentifizierung & Passkey / WebAuthn (`Auth` & `UserLogin`)

### 11.1 Anonymer & Angemeldeter Nutzer (🌐 / 👤)
- [x] **TC-AU-01 [🌐]:** Aufrufen des Login-Formulars. Eingabe von Benutzername und Passwort oder Nutzung des Passkey (WebAuthn / FIDO2) Logins.
- [x] **TC-AU-02 [🌐]:** Anmeldung mit gültigen Zugangsdaten. Die Anwendung wechselt in den angemeldeten Zustand, Admin-Funktionen werden freigeschaltet.
- [x] **TC-AU-03 [🌐]:** Registrierung eines neuen Benutzers (`UserRegistration`). Überprüfung von E-Mail-Validierung und Datenschutz-Zustimmung.
- [x] **TC-AU-04 [👤]:** Klick auf „Abmelden“. Die Session wird beendet, SessionStorage-Einträge gelöscht und die Ansicht auf die Startseite zurückgesetzt.

### 11.2 Automatisierte Benutzerregistrierung via Mailcatcher (`UserRegistration` & `VerifyAccount`)
- [x] **TC-REG-01 [🌐]:** Mailcatcher REST-API ist erreichbar und einsatzbereit.
- [x] **TC-REG-02 [🌐]:** Passwortbasierte Registrierung (`subscriber`), Empfang der Aktivierungsmail via Mailcatcher SMTP, Verifizierung per E-Mail-Link, Auto-Login & anschließender Passwort-Login.
- [x] **TC-REG-03 [🌐]:** Passwortlose Registrierung, E-Mail-Empfang und Aktivierung via Verifizierungslink.
- [x] **TC-REG-04 [🌐]:** Registrierung als Turnierverwalter (`tourney_admin`) mit Veranstalter & Admin-Prüfung („Dein Account wird nun vom Administrator geprüft“).

---

## 12. UseCase: Systemadministrator-Verwaltung (`Management`)

### 12.1 Systemadministrator (👑)
- [x] **TC-MG-01 [👑]:** Aufrufen von `Management` als `administrator` oder `tourney_master`. Die Admin-Übersicht lädt.
- [x] **TC-MG-02 [👑]:** Anzeigen der Liste aller registrierten Turnierverwalter und Benutzer.
- [x] **TC-MG-03 [👑]:** Anpassen des Turnier-Guthabens (`available`) eines Benutzers. Das neue Guthaben wird im Backend gespeichert und dem Nutzer gutgeschrieben.
- [x] **TC-MG-04 [👑]:** Einsicht in Payhip-Zahlungsvorgänge („Unmatched Payhip Purchases“). Zuordnen von Zahlungen zu Benutzerkonten.
- [x] **TC-MG-05 [👑]:** Einsicht in System-Logs und globale Turnierstatistiken.

### 12.2 Nicht-Admin Nutzer (🌐 / 👤)
- [x] **TC-MG-06 [🌐/👤]:** Versuch, den URL-Hash `#Management` ohne ausreichende Admin-Rechte aufzurufen. Der Zugriff wird verweigert und die Anwendung leitet auf die Startseite um.

---

## 13. Cross-Browser & Responsiveness (Viewport-Testing)

### 13.1 Anonymer & Angemeldeter Nutzer (🌐 / 👤)
- [x] **TC-CB-01 [🌐]:** Mobile Viewport (375x667) & Hamburger-Menü. Validierung von sichtbarem Menü-Toggler und aufklappbaren Links im mobilen Layout.
- [x] **TC-CB-02 [🌐]:** Tablet Viewport (768x1024) & Grid-Anpassung. Validierung von mehrspaltigen Layouts und Formularelementen.
- [x] **TC-CB-03 [🌐]:** Desktop Viewport (1280x800) & Nebeneinander-Darstellung. Vollständige Sichtbarkeit der Haupt-Navigationsleiste ohne Hamburger-Button.
- [x] **TC-CB-04 [🌐]:** Hoch- / Querformat-Umschaltung (Portrait vs. Landscape). Dynamische Anpassung des Viewports ohne Layout-Abbrüche.

---

## 14. Netzwerk-Resilienz & Error Handling (API Interception)

### 14.1 Anonymer & Angemeldeter Nutzer (🌐 / 👤)
- [x] **TC-NR-01 [🌐]:** Injektion von Server-Fehlern (HTTP 500 / 503). Simulation von Serverausfällen via `page.route()`, UI bleibt stabil und zeigt Fehlermeldungen.
- [x] **TC-NR-02 [🌐]:** Injektion von Rate-Limiting (HTTP 429 Too Many Requests). Verifizierung der Account-Lockout Fehlermeldung bei geblocktem Login.
- [x] **TC-NR-03 [🌐]:** Simulation von hoher Netzwerk-Latenz (Throttling / Delay). Verifizierung, dass die UI auch bei verlangsamter API-Antwort stabil bleibt.
- [x] **TC-NR-04 [🌐]:** Simulation von Netzwerkausfall / Verbindungsabbruch (Network Abort). Prüfung, ob UI Verbindungsunterbrechungen abfängt.

---

## 15. Session-Management, Cookies & Security Route Guards

### 15.1 Anonymer & Angemeldeter Nutzer (🌐 / 👤)
- [x] **TC-SM-01 [🌐]:** Session-Management & Storage-Clearance bei Logout. Automatisches Löschen aller Cookies, `sessionStorage` und `localStorage` Tokens.
- [x] **TC-SM-02 [🌐]:** Route Guard `#Management` (Zugriffsschutz für geschützte Admin-URL). Unbefugter Direktaufruf wird abgefangen und umgeleitet.
- [x] **TC-SM-03 [🌐]:** Route Guard `#TourneyAdmin` (Zugriffsschutz für fremde Turnierverwaltung). Unbefugter Aufruf leitet auf `TourneyWelcome` um.

---

## 16. Input-Boundary, Edge Cases & Sicherheits-Injektionen

### 16.1 Anonymer & Angemeldeter Nutzer (🌐 / 👤)
- [x] **TC-IB-01 [🌐]:** Extreme String-Längen im Suchfeld (Boundary-Testing). Eingabe von 500+ Zeichen bricht die UI nicht und erzeugt keine JS-Fehler.
- [x] **TC-IB-02 [🌐]:** XSS & HTML-Injektionen (`<script>alert(1)</script>`). Maskierung von Script-Payloads und HTML-Attributen in Eingabefeldern.
- [x] **TC-IB-03 [🌐]:** Unicode, Emojis & Sonderzeichen (`äöüß`, `🏆`, `@#$&*()!`). Korrekte Verarbeitung und Anzeige von Unicode-Zeichenketten.
- [x] **TC-IB-04 [🌐]:** Dateivalidierung beim Spielerimport (`setInputFiles()`). Überprüfung der Validierungslogik bei ungültigen Dateiformaten.

---

## 17. UseCase: Turnierübersicht als Baum (`TourneyInfo`)

### 17.1 Turnierverwalter (👤)
- [x] **TC-TI-TREE-01 [👤]:** In der Wettbewerbsliste steht vor jedem Wettbewerb mit Stages ein Tree Node Glyph (TNG). Klick klappt die Stages (mit eigenem TNG, Format und Status) auf; Klick auf den TNG einer Stage zeigt deren Reiter (Administration, Auslosung, Eingabe, Ergebnisse, Schiri, ggf. Urkunde). Klick auf einen Reiter wählt Wettbewerb und Stage aus und öffnet die zugehörige Stage-Seite mit aktivem Reiter.
- [x] **TC-TI-TREE-02 [👤]:** Erneuter Klick auf den TNG eines Wettbewerbs klappt dessen Stages wieder zu.

---

## 18. UseCase: Nutzungsstatistik (`stats-hit`, Shortcode `[tourney_stats]`)

### 18.1 Anonymer Nutzer (🌐) & Administrator (👑)
- [x] **TC-STAT-01 [🌐]:** Der Aufruf der Homepage wird einmal pro Browsersitzung gemeldet (`stats-hit?e=home`), Neuladen meldet nicht erneut, eine neue Sitzung wieder.
- [x] **TC-STAT-02 [🌐]:** Klicks auf „Demo“, „Turnier anlegen“ und „Wettbewerb starten“ werden gemeldet (`demo`, `tourney`, `comp`) und erhöhen den Tageszähler.
- [x] **TC-STAT-03 [🌐]:** Unbekannte Ereignisse und Bot-User-Agents werden ignoriert (Antwort immer 204).
- [x] **TC-STAT-04 [👑]:** Shortcode `[tourney_stats]` zeigt Tageswerte (30 Tage) und Wochenwerte (12 ISO-Wochen) mit Summenzeile, nur für Administratoren.
- [x] **TC-STAT-05 [👑]:** Eine private WordPress-Seite mit `[tourney_stats]` wird auch in der Website-Variante im Theme angezeigt (nicht als App); anonym ist sie nicht sichtbar.

---

## 19. UseCase: Stage-Reiter Urkunden (`StageCertificate`)

### 19.1 Turnierverwalter (👤)
- [x] **TC-CERT-TAB-01 [👤]:** Der Reiter „Urkunden“ (nur bei der Urkunden-Stage) zeigt dieselbe Ansicht wie „Ergebnisse“; bei Jeder gegen Jeden hat jede Zeile mit Platzierung einen Urkunden-Button, der die Urkunde mit Name und Platz öffnet. Der Button „Nächste Stage“ fehlt.
- [x] **TC-CERT-TAB-02 [👤]:** K.o.-System: Turnierbaum wie unter „Ergebnisse“; Urkunden-Buttons nur bei Spielern, deren Endplatzierung im Spiel feststeht (Finale 1/2, Spiel um Platz 3 3/4, sonst Verlierer der Runde).

---

## 20. SEO der Website-Variante (`includes/seo.php`)

### 20.1 Anonymer Nutzer / Crawler (🌐)
- [x] **TC-SEO-01 [🌐]:** Startseite liefert im Server-HTML genau einen `<title>`, eine Meta-Description, Open-Graph- und Twitter-Card-Tags (Vorschaubild `img/og-image.jpg` erreichbar) sowie JSON-LD `WebApplication` und `Organization`.
- [x] **TC-SEO-02 [🌐]:** Login, Registrierung, Verifizierung und Vorlagen-Seiten haben `noindex` und erscheinen nicht in der Sitemap.
- [x] **TC-SEO-03 [🌐]:** Keine Benutzer-Sitemap; `/?author=N` und `/author/<name>/` leiten auf die Startseite um, ohne den Anmeldenamen zu verraten; `/wp-json/wp/v2/users` ist für anonyme Besucher nicht verfügbar.
- [x] **TC-SEO-04 [🌐]:** Turnierseiten haben einen Titel und eine Beschreibung aus den Turnierdaten (Name, Datum, Veranstalter, Wettbewerbe).
- [x] **TC-SEO-05 [🌐]:** Die Startseite liefert Überschrift (`<h1>`) und die vier Karten schon im Server-HTML (statt „Wird geladen…“, Texte aus `msgs_de.json`); nach dem Start ersetzt der Client den Inhalt vollständig (keine Platzhalter-Buttons, genau eine `<h1>`).

---

## 21. Statische Seiten mit eigener URL (`includes/static-pages.php`)

### 21.1 Anonymer Nutzer / Crawler (🌐)
- [x] **TC-STATIC-01 [🌐]:** `/funktionen/`, `/preise/`, `/excel/`, `/plugin/`, `/historie/`, `/impressum/`, `/datenschutz/`, `/kontakt/` und `/agb/` liefern Status 200, genau einen passenden `<title>`, eine Canonical-URL, eine Meta-Description und den Seiteninhalt (mit `<h1>`) im Server-HTML; alle stehen in `wp-sitemap-tourneystatic-1.xml`. Unbekannte URLs liefern weiterhin 404.
- [x] **TC-STATIC-02 [🌐]:** Ein direkter Aufruf (z. B. `/preise/`) öffnet die Seite in der App; die Adresse bleibt erhalten.
- [x] **TC-STATIC-03 [🌐]:** Footer- und Navbar-Links haben echte URLs (`href="/impressum/"`); ein Klick bleibt in der App (kein Neuladen) und setzt die Adresszeile; Zurück führt zur Startseite, Vor wieder zur Seite; der Breadcrumb „Startseite“ setzt die Adresse auf `/`.

---

## 22. Sprachfassungen (`includes/lang.php`)

### 22.1 Anonymer Nutzer / Crawler (🌐)
- [x] **TC-LANG-01 [🌐]:** `/en/` und `/en/features/`, `/en/pricing/`, `/en/excel/`, `/en/plugin/`, `/en/history/`, `/en/imprint/`, `/en/privacy/`, `/en/contact/`, `/en/terms/` liefern Status 200 mit `<html lang="en-GB">`, englischem Inhalt, Canonical-URL, `og:locale en_GB` und gegenseitigen `hreflang`-Verweisen (de, en, x-default = de); die englischen URLs stehen in der Sitemap. Deutsche Slugs unter `/en/` liefern 404.
- [x] **TC-LANG-02 [🌐]:** Der Besuch einer `/en/`-URL merkt Englisch (localStorage `tourney_lang`); deutsche URLs zeigen trotzdem Deutsch (mit Hinweis auf die englische Fassung); Seiten ohne Sprach-URL (z. B. Login, Turnierseiten) nutzen die gemerkte Sprache, sonst die Browsersprache.
- [x] **TC-LANG-03 [🌐]:** Der Sprachumschalter DE | EN in der Navbar führt zur Gegenstück-URL (`/impressum/` ↔ `/en/imprint/`) und merkt die Wahl.
- [x] **TC-LANG-04 [🌐]:** Englischsprachige Besucher ohne gemerkte Wahl sehen auf deutschen Seiten den Hinweis „This page is also available in English“ (Link auf die englische Fassung); Schließen merkt Deutsch ohne Neuladen. Deutschsprachige Besucher sehen keinen Hinweis.

---

## 23. UseCase: Gekoppelte Stages – Doppelte Runde und Doppeltes K.-o. (`doc/DoubleModes.md`)

### 23.1 Turnierverwalter (👤)
- [x] **TC-DM-01 [👤]:** Spielmodus „Doppelte Runde“: Der Start der Spielphase benennt die Stage in „<Name> – Hinrunde“ um und legt „<Name> – Rückrunde“ mit denselben Paarungen (Heim/Gast getauscht, Spielnummern ab 1) im Status Auslosung an. Umschalter Hinrunde | Rückrunde wechselt auf dieselbe Seite der anderen Runde. Die Auslosung der Rückrunde ist nicht änderbar (kein Start), die Administration zeigt die Kopplung, Auslosung/Konfiguration löschen ist gesperrt. In `CompetitionInfo` steht die Rückrunde eingerückt mit Verbindungssymbol unter der Hinrunde; „Nachfolger starten“ gibt es nur an der Rückrunde.
- [x] **TC-DM-02 [👤]:** Nach allen Ergebnissen der Hinrunde wird die Rückrunde spielbar. Die Ergebnisse der Hinrunde zeigen statt Nachfolger/Finalisieren einen Hinweis auf die Rückrunde. Die Ergebnisse der Rückrunde zeigen standardmäßig die Gesamttabelle (je Teilnehmer alle Spiele beider Runden), umschaltbar auf nur Rückrunde. Der Filter „Obere Hälfte“ eines Nachfolgers nutzt die Gesamttabelle.
- [x] **TC-DM-03 [👤]:** Spielmodus „Doppeltes K.-o.“, 8 Spieler: Der Start benennt die Stage in „<Name> – Gewinnerrunde“ um (7 Spiele, kein Spiel um Platz 3) und legt „<Name> – Verliererrunde“ (6 Spiele + Finale) an. Die Verlierer der WB-Runde 1 spielen paarweise in LB-Runde 1, die Verlierer der Halbfinals steigen gekreuzt in LB-Runde 2 ein, der Verlierer des WB-Finales in LB-Runde 4; der WB-Sieger steht im Finale auf Position A. Platzierung nach allen Spielen: 1, 2, 3, 4, 5, 5, 7, 7.
- [x] **TC-DM-04 [👤]:** 6 Spieler (Freilose): Freilose der Gewinnerrunde werden in der Verliererrunde durchgereicht; nach allen Spielen sind genau die 6 Spieler platziert (Plätze 1–4 eindeutig), kein Freilos in der Platzierung.
- [x] **TC-DM-05 [👤]:** Option „Finale mit Rückspiel“ (4 Spieler): Gewinnt der LB-Sieger das erste Finale, wird das Rückspiel mit denselben Spielern spielbar und entscheidet Platz 1/2. Gewinnt der WB-Sieger das erste Finale, ist das Rückspiel „nicht erforderlich“.
- [x] **TC-DM-06 [👤]:** Löschen der Hinrunde fragt nach („gemeinsam gelöscht“) und löscht Hin- und Rückrunde.
- [x] **TC-DM-07 [👤]:** „Auslosung löschen“ an der Hinrunde löst die Kopplung: Die Rückrunde wird gelöscht, der Namenszusatz „– Hinrunde“ entfällt.
- [x] **TC-DM-08 [👤]:** Doppeltes K.-o.: Löschen eines WB-Ergebnisses, dessen Verlierer in der Verliererrunde schon gespielt hat, weist in der Rückfrage darauf hin und setzt das abhängige LB-Spiel zurück.

---

## 24. UseCase: Mannschaftsaufstellung (`DlgLineup`)

### 24.1 Turnierverwalter (👤)
- [x] **TC-TL-01 [👤]:** 6er-Paarkreuz: Ein in einem Doppel eingesetzter Spieler ist in den übrigen Doppeln gesperrt (im Einzel weiterhin wählbar) und wird nach Abwahl wieder frei. Steht ein Spieler dennoch in zwei Doppeln, verweigert „Übernehmen“ mit „Ein Spieler kann nur ein Doppel spielen.“
- [x] **TC-TL-02 [👤]:** „DEMO-Ergebnisse“ bei 6er-Mannschaften: fehlende Aufstellungen beider Mannschaften werden gesetzt (jeder Spieler nur in einem Doppel), die Begegnungen gestartet und alle Ergebnisse eingetragen; eine schon abgegebene Aufstellung bleibt erhalten.

---

## 25. UseCase: ClickTT in der Turnierübersicht (`TourneyInfo`, `ClickTTPanel`)

### 25.1 Turnierverwalter (👤)
- [x] **TC-CTT-01 [👤]:** Bei Sportart Tischtennis steht im Card-Header von `TourneyInfo` vor „Bearbeiten“ das Dropdown „ClickTT“ mit „Import“, „Update“ und „Export“. Die Administration hat keinen Reiter „ClickTT Update“ mehr.
- [x] **TC-CTT-02 [👤]:** „Import“ öffnet den Dialog `DlgClickTT`; eine ClickTT-XML wird eingelesen („… Wettbewerbe gefunden“), „Daten übernehmen“ legt daraus ein neues Turnier an.
- [x] **TC-CTT-03 [👤]:** „Update“ blendet unter der Turnier-Card den Bereich „ClickTT Update“ ein; eine XML-Datei wird eingelesen (im Demo-Turnier: „keine Spieler ohne ID/Lizenznummer“). Das Schließen-Kreuz blendet den Bereich aus.
- [x] **TC-CTT-06 [👤]:** „Update“ mit einem Spieler ohne Lizenznummer zeigt die Zuordnungstabelle; nach Auswahl und „Zuordnungen speichern“ ist der Spieler zugeordnet und der Bereich geschlossen.
- [x] **TC-CTT-04 [👤]:** „Export“ blendet den Bereich „ClickTT Export“ ein; nach „ClickTT XML Ergebnis Datei erzeugen“ und Auswahl der Abschnitte wird `clicktt_export_ergebnisse_<id>.xml` heruntergeladen.
- [x] **TC-CTT-05 [👤]:** Bei anderer Sportart gibt es kein ClickTT-Dropdown; `TourneyNew` hat keinen Button „Lade ClickTT-Daten“ mehr.

---

## 26. UseCase: Multiuser-Betrieb (`services/MultiUser`, `StageDB`, `includes/api/changelog.php`)

Zwei angemeldete Bearbeiter (A, B) im selben Cloud-Turnier auf turnier-service.info (nicht Embed-Plugin, Demo- oder Lokalmodus). Änderungen laufen über den WebSocket des Play-Servers (`SYNC:`-Meldung, Weiterleitung an die anderen Clients des Turniers); die Daten liegen weiter in WordPress.

### 26.1 Turnierverwalter (👤)
- [x] **TC-MU-01 [👤]:** A trägt ein Ergebnis ein; bei B erscheint es ohne Neuladen in der Ergebniseingabe, dazu der Hinweis „<Name>: <Wettbewerb> – <Stage>: Spiel n …“.
- [x] **TC-MU-02 [👤]:** Bei der Aktualisierung durch eine Änderung von A bleiben bei B eingegebene, noch nicht gespeicherte Werte erhalten.
- [x] **TC-MU-03 [👤]:** B arbeitet auf altem Stand (Meldung unterdrückt) und speichert ein anderes Spiel derselben Stage: WordPress meldet einen Konflikt (409), B führt die Änderungen je Spiel zusammen und speichert erneut; beide Ergebnisse bleiben erhalten.
- [x] **TC-MU-04 [👤]:** A und B tragen dasselbe Spiel unterschiedlich ein: Die letzte Eingabe (B) gilt, B erhält den Hinweis „… Ihre Eingabe gilt: … Spiel n“, A zeigt danach das Ergebnis von B.
- [x] **TC-MU-05 [👤]:** `TourneyAdmin`, Reiter „Änderungen“: Das Protokoll zeigt Zeit, Bearbeiter und Ergebnis jeder Eingabe (neueste zuerst), bei Spiel n beide Eingaben.

---

## 27. UseCase: Öffentliche Anmeldung zu Wettbewerben (`TourneyWelcome`, `DlgPublicRegistration`, `DlgCompetition`)

Anmeldung nur für Einzel, wenn turnierweit erlaubt (TourneyAdmin, Reiter „Homepage“), der Wettbewerb noch nicht begonnen hat und nach den Einstellungen des Wettbewerbs offen ist (Zeitraum „Anmeldung ab/bis“, Checkbox „Anmeldung beendet“). Der Server (`public-register`) prüft dieselben Bedingungen.

### 27.1 Anonymer Besucher (🌐)
- [x] **TC-PR-01 [🌐]:** Wettbewerbsliste: „Jetzt anmelden“ mit „bis TT.MM.JJJJ“ bei offenem Wettbewerb, „Anmeldeschluss“ bei beendeter Anmeldung, „Anmeldung ab TT.MM.JJJJ“ vor Beginn des Zeitraums, kein Button bei Doppel.
- [x] **TC-PR-02 [🌐]:** Anmeldedialog prüft Pflichtfelder (Name, Vorname, Verein, TTR) sowie das Format von E-Mail und WhatsApp-Nummer.
- [x] **TC-PR-03 [🌐]:** Anmeldung mit freiwilligen Angaben (Jahrgang, E-Mail, WhatsApp) nach Captcha: Spieler mit diesen Angaben und Teilnehmer mit TTR und Status „registriert“ (`REGI`) im Wettbewerb gespeichert; der Dialog zeigt den Standardhinweis („Bitte spätestens 30 Min vor Beginn …“) und bleibt offen, bis der Spieler „Schließen“ klickt.
- [x] **TC-PR-04 [🌐]:** E-Mail und WhatsApp-Nummer der Spieler sind für Besucher (ohne Schreibrecht) über `GET players` nicht abrufbar.
- [x] **TC-PR-05 [🌐]:** Der Server lehnt Anmeldungen zu beendeten, noch nicht geöffneten und Doppel-Wettbewerben ab (`registration_closed`).

### 27.2 Turnierverwalter (👤)
- [x] **TC-PR-06 [👤]:** „Wettbewerb bearbeiten“ zeigt den Bereich „Online-Anmeldung“ (Anmeldung ab/bis, „Anmeldung beendet“); nach Setzen der Checkbox ist die Anmeldung auf der Turnierseite geschlossen.
- [x] **TC-PR-07 [👤]:** Ist die Anmeldung turnierweit nicht erlaubt, sieht der Turnierleiter auf der Turnierseite statt „Anmeldeschluss“ den Hinweis „Anmeldung nicht freigeschaltet – Turnier-Administration → Homepage“ (nicht bei beendeter Anmeldung oder Doppel); Besucher sehen weiterhin „Anmeldeschluss“.
- [x] **TC-PR-08 [👤]:** Turnier-Administration, Reiter „Homepage“: Feld „Hinweis nach der Online-Anmeldung“ ist mit dem Standardtext vorbelegt; ein eigener Text wird gespeichert und nach der nächsten Anmeldung im Dialog angezeigt.

---

## 28. UseCase: „Mein Turnier“ – persönliche Ansicht für Spieler (`MyTourney`)

Button „Mein Turnier“ auf der Turnierseite (`TourneyWelcome`, bei Einzel-Wettbewerben auch `CompetitionWelcome`). Der Besucher wählt seinen Namen (ohne Konto, je Turnier im Browser gespeichert) und sieht seine nächsten Spiele, Ergebnisse und Wettbewerbe mit Tabellenplatz bzw. Stand im K.-o.-Baum. Einzel und Doppel; Mannschaftswettbewerbe nur in der Liste. Die Daten werden alle 30 Sekunden neu geladen.

### 28.1 Anonymer Besucher (🌐)
- [x] **TC-MT-01 [🌐]:** Name wählen: nächste Spiele mit Gegner, Wettbewerb, Spielphase und Gruppe („Spielbereit“ vor „Wartet“), Ergebnis aus eigener Sicht (3:0, grün bei Sieg) und Tabellenplatz in der Gruppe mit Punkten.
- [x] **TC-MT-02 [🌐]:** Ein aufgerufenes Spiel steht oben mit „Aufgerufen – Tisch n“.
- [x] **TC-MT-03 [🌐]:** K.-o.: „Nächste Runde: Halbfinale“ bzw. „Ausgeschieden: Viertelfinale“; ein Freilos erscheint als „Freilos“ ohne Ergebnis.
- [x] **TC-MT-04 [🌐]:** Die Auswahl bleibt nach erneutem Aufruf erhalten; „Anderer Name“ setzt sie zurück.
- [x] **TC-MT-05 [🌐]:** Ruft die Turnierleitung ein Spiel auf, erscheint „Aufgerufen – Tisch n“ ohne Neuladen nach spätestens 30 Sekunden.

---

## 29. UseCase: Eigenes Logo eines Turniers (`TourneyAdmin` Reiter „Homepage“, `includes/api/logo.php`)

Nur Cloud-Turniere. Das Logo liegt als Anhang des Turnier-Posts in der Mediathek (PNG, JPG oder WebP, höchstens 2 MB, auf 400 px verkleinert) und erscheint auf der Turnierseite und auf den Urkunden (Standardurkunde statt des Pokal-Symbols, eigene Vorlagen über den Platzhalter `#Logo`).

### 29.1 Turnierverwalter (👤) / Besucher (🌐)
- [x] **TC-LOGO-01 [👤]:** Logo hochladen: Vorschau im Reiter „Homepage“, Bild auf 400 px verkleinert, Adresse über `read-aci` (`logoUrl`).
- [x] **TC-LOGO-02 [🌐]:** Das Logo erscheint auf der Turnierseite (`TourneyWelcome`).
- [x] **TC-LOGO-03 [👤]:** Die Standardurkunde zeigt das Logo statt des Pokal-Symbols.
- [x] **TC-LOGO-04 [👤]:** Falsches Format wird abgelehnt: im Client mit Hinweis, auf dem Server anhand des Inhalts (`logo_type`).
- [x] **TC-LOGO-05 [🌐]:** Hochladen ohne Schreibrecht auf das Turnier wird abgelehnt (401/403).
- [x] **TC-LOGO-06 [👤]:** Ein neues Logo ersetzt das alte (alte Datei gelöscht); Speichern der Homepage-Einstellungen lässt das Logo unverändert; „Entfernen“ löscht Logo und Datei.
- [x] **TC-LOGO-07 [👤]:** Beim endgültigen Löschen des Turniers wird das Logo mit gelöscht.

---

## 30. UseCase: Abrechnung der Cloud-Turniere und Archiv (`includes/user.php`, `cpt.php`, Kostenseite)

Guthaben in Posten (gekaufte Turniere 2 Jahre gültig, Altbestand und manuelle Gutschriften unbefristet). Beim Anlegen wird ein Turnier reserviert; wird es ohne Ergebnis gelöscht, kommt es zurück. Mit Vereins-/Verbandslizenz (Feld „gültig bis“ im Benutzerprofil bzw. in der Management-Konsole) wird kein Guthaben verbraucht und nicht archiviert. Ein Turnier ist 1 Jahr nach Turnierende (frühestens 1 Jahr nach dem Anlegen) archiviert: schreibgeschützt, öffentlich lesbar.

### 30.1 Turnierverwalter (👤) / Administrator (👑)
- [x] **TC-CR-01 [👤]:** Ohne Guthaben wird das Anlegen abgelehnt (`tourney_limit_reached`); ein Kauf ergibt einen Posten mit Ablauf in 2 Jahren; Anlegen reserviert 1 Turnier, Löschen ohne Ergebnis gibt es mit demselben Ablaufdatum zurück.
- [x] **TC-CR-02 [👤]:** Nach dem ersten Ergebnis (`stages-sync`, `_has_results`) gibt das Löschen kein Guthaben zurück.
- [x] **TC-CR-03 [👤]:** Abgelaufenes Guthaben zählt nicht; das Anlegen wird abgelehnt.
- [x] **TC-CR-04 [👑]:** Lizenz über die Management-Konsole (`admin/update_user_profile`, `license_until`): Anlegen ohne Guthaben, kein Verbrauch.
- [x] **TC-AR-01 [👤]:** Archiv: mit Lizenz nicht archiviert; ohne Lizenz `read-aci` → `archived`, Schreiben als Turnierleiter 403 (`tourney_archived`), als Administrator erlaubt; der Turnierleiter sieht die öffentliche Seite mit Hinweis „Archiv“ (`data-canedit="0"`).

---

## 31. UseCase: Hilfe & Benutzerhandbuch (`site/content/hilfe.html`)

Statische Seiten `/hilfe/` und `/en/help/` (`hilfe_en.html`), Menüpunkt „Hilfe“/„Help“. Die Screenshots erzeugt `test/e2e/docs` aus dem Demo-Turnier bzw. einem vorübergehenden Cloud-Turnier: `npx playwright test --config docs.config.ts` (Deutsch, `site/content/hilfe/`), mit `HELP_LANG=en` Englisch (`site/content/help/`).

### 31.1 Besucher (🌐)
- [x] **TC-HELP-01 [🌐]:** Der Menüpunkt „Hilfe“ öffnet das Handbuch; alle 18 Einträge des Inhaltsverzeichnisses haben ein Ziel, alle 20 Screenshots werden geladen.
- [x] **TC-HELP-02 [🌐]:** `/hilfe/` und `/en/help/` sind indexierbar (Canonical, kein noindex), per hreflang verknüpft und stehen in der Sitemap.
- [x] **TC-HELP-03 [🌐]:** Die englische Fassung hat 18 Abschnitte und lädt alle 20 englischen Screenshots (`pages/help/`).

---

## 32. UseCase: Turnier kopieren – Turniervorlage aus dem Vorjahr (`TourneyInfo`, `services/TourneyCopy`, `shared/model/TourneyTemplate`)

Button „Kopieren“ in der Turnierübersicht: Dialog mit Name und Startdatum (Vorschlag: gleicher Wochentag ein Jahr später, Jahreszahl im Namen angepasst). Übernommen werden Stammdaten, Ort, Kontakt und Wettbewerbe ohne Spieler, Mannschaften und Ergebnisse; Start- und Anmeldezeiten werden verschoben. Die Spielphasen bleiben als Phasenplan im Wettbewerb und werden beim Start vorbelegt (Name, Spielsystem, Gewinnsätze). Cloud → neues Cloud-Turnier mit Homepage-Einstellungen und Logo; lokal → neues lokales Turnier (mit Rückfrage zum Speichern). Unit-Tests: `TourneyTemplateSuite`.

### 32.1 Turnierverwalter (🌐 / 👤)
- [x] **TC-COPY-01 [🌐]:** Lokales Demo-Turnier kopieren: Vorschlag 2031-03-31; nach der Rückfrage „überschreiben“ öffnet sich das neue Turnier mit 4 Wettbewerben in Konfiguration, ohne Teilnehmer, Startzeit verschoben; „Wettbewerbsabschnitt starten“ schlägt „Vorrunde“ (Jeder gegen Jeden) und danach „Endrunde“ (K.-o.) vor.
- [x] **TC-COPY-02 [👤]:** Cloud-Turnier kopieren: neues Cloud-Turnier mit 4 Wettbewerben (Status Konfiguration, Phasenplan, verschobene Startzeit), ohne Spieler und Spielphasen, mit Homepage-Text, Anmeldeeinstellung und eigener Kopie des Logos; das Quellturnier bleibt unverändert.

---

## 33. UseCase: Online-Anmeldung mit Warteliste (`Competition.maxPants`, `public-register`, `includes/api/waitlist.php`, `services/Waitlist`)

Im Dialog „Wettbewerb bearbeiten“ legt der Turnierleiter eine Höchstzahl Teilnehmer fest (leer = unbegrenzt). Ist sie erreicht, kommen weitere Online-Anmeldungen mit Status WAIT (nicht aktiv) auf die Warteliste. Entfernt der Turnierleiter einen Teilnehmer oder erhöht er die Höchstzahl, rücken Spieler in Anmeldereihenfolge nach (Status REGI, aktiv). Mit E-Mail-Adresse gibt es Mails: Bestätigung, Wartelistenplatz, Nachrücken (Dev: Mailcatcher). Unit-Tests: `WaitingListSuite`.

### 33.1 Besucher (🌐) / Turnierverwalter (👤)
- [x] **TC-WL-01 [🌐]:** Freier Platz: Turnierseite zeigt „1 von 1 Plätzen frei“ (ohne Höchstzahl keine Angabe); Anmeldung mit Status REGI und Mail „Anmeldung bestätigt“.
- [x] **TC-WL-02 [🌐]:** Ausgebucht: „Ausgebucht – 0 auf der Warteliste“, Button „Auf die Warteliste“, Hinweis im Dialog; nach der Anmeldung „Platz 1 der Warteliste“, Status WAIT (nicht aktiv), Mail „Warteliste“.
- [x] **TC-WL-03 [👤]:** Anmeldeliste markiert „Warteliste 1“; nach dem Entfernen des Teilnehmers rückt die Spielerin nach (REGI, aktiv), `waitlist-notify` meldet 1 Mail, Mail „Nachgerückt“.
- [x] **TC-WL-04 [👤]:** Höchstzahl im Dialog „Wettbewerb bearbeiten“ speichern (`maxPants` 16); Turnierseite zeigt „16 von 16 Plätzen frei“.

## 34. UseCase: Statische Seite „Wertungssystem“ (`site/content/wertung.html`, `wertung_en.html`; `scoring_page.spec.ts`)
### 34.1 Besucher (🌐)
- [x] **TC-WRT-01 [🌐]:** Die Hilfe (Abschnitt 6) verweist auf `/wertung/`; die Seite hat 11 Abschnitte im Inhaltsverzeichnis (alle Ziele vorhanden), Kurzschreibweise, Tabellenkriterien und Spielsysteme für Mannschaften; Abschnitt 9 der Hilfe verweist auf `/wertung/#tabelle`.
- [x] **TC-WRT-02 [🌐]:** `/wertung/` und `/en/scoring/` liefern 200, sind indexierbar, haben Canonical und hreflang aufeinander und stehen in der Sitemap.
- [x] **TC-WRT-03 [🌐]:** Die englische Hilfe verweist auf `/en/scoring/` mit englischem Inhalt.

## 35. UseCase: Bezeichnungen aus dem Wertungssystem (`base.SportText`, `shared/sports/tt_00n.json`; `sport_labels.spec.ts`)
### 35.1 Turnierverwalter (👤), deutsch und englisch
- [x] **TC-SPN-01 [👤]:** Die Turnierübersicht zeigt das Format der Wettbewerbe aus dem Wertungssystem („Einzel“/„Doppel“ bzw. „Singles“/„Doubles“).
- [x] **TC-SPN-02 [👤]:** Der Tabellenkopf einer Jeder-gegen-jeden-Runde nennt die Stufen aus dem Wertungssystem („Spiele“, „Sätze“, „Bälle“ bzw. „Matches“, „Games“, „Points“).
- [x] **TC-SPN-03 [👤]:** Die Auswahl der Gewinnsätze in der Stage-Administration kommt aus den Varianten („2/3/4 Gewinnsätze“ bzw. „Best of 3/5/7“, Werte 2–4, Standard 3).

## 36. UseCase: Direkter Vergleich bei Punktgleichheit (Wertungssystem `tt_003`, Kriterium `headToHead`; `head_to_head.spec.ts`)
Tabellenberechnung: Unit-Tests `shared/src/test/scala/shared/format/HeadToHeadSuite.scala` (2 Gleichstehende, Kreis aus 3, Neubeginn nach Abtrennung (ITTF 3.7.5.4), Doppelte Runde, nicht auflösbar, ältere Versionen unverändert, Mannschaften ohne direkten Vergleich).
### 36.1 Turnierverwalter (👤)
- [x] **TC-H2H-01 [👤]:** Ein neu angelegter Wettbewerb erhält das Wertungssystem `tt_003` („Version 3“) in der Stage-Administration.
- [x] **TC-H2H-02 [👤]:** Ein bestehender Wettbewerb (Demo, ohne Eintrag) behält seine Version (`tt_001`).

## 37. UseCase: Durchsagen der Turnierleitung (`includes/api/announcement.php`, `services.Announcements`; `announcements.spec.ts`)
### 37.1 Turnierverwalter (👤) / Besucher (🌐)
- [x] **TC-ANN-01 [👤]:** Durchsage in der Turnierübersicht (Karte „Durchsagen“, höchstens 200 Zeichen) senden: erscheint in der Liste, ist in WordPress gespeichert (`GET announcements`) und steht als Info-Eintrag auf dem Matchboard (Play-Server).
- [x] **TC-ANN-02 [🌐]:** „Mein Turnier“ zeigt die Durchsage oben ohne Anmeldung (ohne Lösch-Button).
- [x] **TC-ANN-03 [🌐]:** Ohne Schreibrecht kein Senden/Löschen (401/403); mehr als 200 Zeichen werden abgelehnt (400); Lesen ist öffentlich.
- [x] **TC-ANN-04 [👤]:** Durchsage löschen: verschwindet aus Liste, WordPress, Matchboard und „Mein Turnier“.
