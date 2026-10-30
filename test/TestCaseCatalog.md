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
- [x] **TC-MV-05 [🌐]:** Umschalten der Sprache in der Navigationsleiste (Deutsch / Englisch). Die UI-Texte und statischen Seiten passen sich der gewählten Sprache an.
- [x] **TC-MV-11 [🌐]:** Status-Zeile vor der Hero-Section: Zeigt den Namen des letzten Turniers (lokal oder Cloud/Slug) und einen Button zum Laden an, jedoch nur, wenn entsprechende Speicherschlüssel im Browserspeicher existieren. Bei Klick auf „Laden“ wird das entsprechende Turnier geladen und direkt zu `TourneyInfo` navigiert. Existieren keine Speicherschlüssel, wird die Status-Zeile nicht angezeigt.
- [x] **TC-MV-11e [🌐]:** Status-Zeile bei einfachem Wettbewerb (`ident == "SIMPLE"`): Status-Zeile zeigt „Letzter Wettbewerb“ (bzw. auf Englisch „Last Competition“) mit Blitz-Icon statt „Letztes Turnier“. Klick auf „Laden“ öffnet direkt `CompetitionInfo` statt `TourneyInfo`.
- [x] **TC-MV-11f [🌐]:** Automatisches Routing für einfache Wettbewerbe: Direkte Navigation zu `TourneyInfo` bei einem einfachen Wettbewerb (`ident == "SIMPLE"`) wird automatisch abgefangen und zu `CompetitionInfo` weitergeleitet.
- [x] **TC-MV-11g [🌐]:** Resilientes Laden von einfachen Wettbewerben: Beim Laden eines einfachen Wettbewerbs (`ident == "SIMPLE"`) über die Status-Zeile oder Navbar werden alle Teildatenbanken vollständig geladen, ohne dass der Dialog „Turnier verlassen“ (`DlgExportTourney`) fälschlicherweise erscheint.

### 1.2 Turnierverwalter (👤)
- [x] **TC-MV-06 [👤]:** Aufrufen der Startseite im angemeldeten Zustand. In der Navigationsleiste werden der Benutzername und der Abmelden-Button angezeigt.
- [x] **TC-MV-07 [👤]:** Klick auf „Guthaben / Vollversion“. Öffnet direkt das Formular zur Erstellung eines neuen Turniers (`TourneyNew`).
- [x] **TC-MV-08 [🌐]:** Klick auf „Wettbewerb starten“ (`#MainView_BtnOption2`) öffnet den Dialog `DlgStartCompetition` („Wettbewerb starten?“, Warnhinweis „Achtung: der aktuelle lokale Wettbewerb bzw das aktuelle lokale Turnier wird gelöscht!“, Buttons „ABBRECHEN“ und „STARTEN“). Bei Klick auf „STARTEN“ wird die Schnellstart-Seite (`CompetitionNew`) zur Wettbewerbserstellung geöffnet (anstelle des `DlgCompetition`-Modals).
- [x] **TC-MV-08b [🌐]:** Schnellstart-Assistent (`CompetitionNew`): Nach Ausfüllen des Formulars und Klick auf „SCHNELLSTART“ wird automatisch das lokale Wrapper-Turnier samt Wettbewerb angelegt und direkt die Ansicht `CompetitionInfo` geöffnet.

### 1.3 Systemadministrator (👑)
- [x] **TC-MV-09 [👑]:** Aufrufen der Startseite als `administrator` oder `tourney_master`. In der Navigationsleiste erscheint zusätzlich der Button „Management“.
- [x] **TC-MV-10 [👑]:** Klick auf den Button „Management“. Die Anwendung wechselt direkt zur Admin-Verwaltungskonsole (`Management`).

### 1.4 Main Navbar: Ablage-Menü (🌐 / 👤)
- [x] **TC-NB-01 [🌐]:** Navbar-Layout: Der alte Start-Dropdown-Button, der Menüpunkt „Suchen“, das Badge „Lokaler Modus“, der Demo-Button sowie der RELOAD-Button in der Navbar wurden entfernt. In der Navbar befindet sich das Dropdown-Menü „Ablage“ (Storage) mit den 4 Einträgen „Letztes Turnier laden“ (in roter Farbe), „Turnier exportieren in Datei (JSON)“, „Turnier von Datei (JSON) laden.“ und „Turnier in die Cloud verschieben“.
- [x] **TC-NB-02 [🌐]:** Demo-Button: Klick auf den „Demo“-Button (im MainView-Container) öffnet den Bestätigungsdialog `DlgStartDemo` („Demo lokal starten?“, Warnhinweis: „Achtung: das aktuelle lokale Turnier wird gelöscht!“) mit 2 Buttons („ABBRECHEN“ und „DEMO STARTEN“). Klick auf „ABBRECHEN“ schließt den Dialog und verbleibt auf der Seite; Klick auf „DEMO STARTEN“ startet das vorkonfigurierte Demo-Turnier direkt im lokalen Modus (`isLocalMode = true`, `isDemoMode = false`), sichert es im lokalen Browserspeicher und navigiert zu `TourneyInfo`.
- [x] **TC-NB-03 [🌐]:** Ablage -> Turnier in die Cloud verschieben (nicht angemeldet): Ist der Benutzer nicht angemeldet, ist das Menüelement ausgegraut (`disabled`, `aria-disabled="true"`).
- [x] **TC-NB-04 [👤]:** Ablage -> Turnier in die Cloud verschieben (bereits in der Cloud): Ist das aktuelle Turnier bereits in der Cloud (`tourney_is_local_mode = false`), ist das Menüelement ebenfalls ausgegraut (`disabled`).
- [x] **TC-NB-04b [👤]:** Ablage -> Turnier in die Cloud verschieben (aktiv): Ist der Benutzer angemeldet und ein lokales Turnier aktiv, ist das Menüelement aktiv und anwählbar.
- [x] **TC-NB-05 [🌐]:** Ablage -> Turnier von Datei (JSON) laden.: Öffnet den Dateiauswahldialog für JSON-Dateien. Eine ausgewählte gültige Turnierdatei wird in den lokalen Modus geladen und direkt in `TourneyInfo` geöffnet.
- [x] **TC-NB-06 [🌐]:** Speicherschlüssel `tourney_slug`: Wird synchron zu `tourney_is_local_mode` gepflegt – bei `tourney_is_local_mode = true` ist `tourney_slug = "local"`, bei `tourney_is_local_mode = false` entspricht `tourney_slug` dem tatsächlichen Turnier-Slug `<slug>`.
- [x] **TC-NB-07 [🌐]:** RELOAD-Button entfernt / Menüpunkt „Letztes Turnier laden“ im lokalen Modus: Bei `tourney_is_local_mode = true` lädt ein Klick auf den Menüpunkt „Letztes Turnier laden“ das im Browserspeicher (`localStorage` / `App.demo_tourney`) abgelegte lokale Turnier und öffnet direkt `TourneyInfo`. Der separate RELOAD-Button ist nicht mehr in der Navbar vorhanden.
- [x] **TC-NB-08 [🌐]:** „Letztes Turnier laden“ im lokalen Modus ohne gespeichertes Turnier: Bei `tourney_is_local_mode = true` und leerem Speicher erscheint ein Hinweisdialog („Kein lokal gespeichertes Turnier vorhanden.“).
- [x] **TC-NB-09 [🌐]:** „Letztes Turnier laden“ im Cloud-Modus ohne Login: Bei `tourney_is_local_mode = false` ohne angemeldeten Benutzer erscheint ein Hinweis („Für das Laden dieses Turniers ist eine Anmeldung erforderlich. Bitte melden Sie sich an.“) und leitet zu `UserLogin` weiter.
- [x] **TC-NB-10 [👤]:** „Letztes Turnier laden“ im Cloud-Modus ohne gespeicherten Slug: Bei `tourney_is_local_mode = false` mit angemeldetem Benutzer, aber fehlendem `tourney_slug` erscheint ein Hinweisdialog („Kein Turnier-Slug gespeichert.“).
- [x] **TC-NB-11 [👤]:** „Letztes Turnier laden“ im Cloud-Modus ohne Zugriffsrechte: Bei `tourney_is_local_mode = false` mit angemeldetem Benutzer, der weder Administrator noch Veranstalter des geladenen Turniers ist, erscheint ein Berechtigungsfehler („Sie haben keine Berechtigung für dieses Turnier.“).
- [x] **TC-NB-12 [👤]:** Ablage -> Turnier in die Cloud verschieben (Ausführung): Klick auf die aktive Option lädt das lokale Turnier in die Cloud hoch, synchronisiert Vereine, Spieler, Wettbewerbe und Phasen, entfernt das lokale Duplikat und schaltet in den Cloud-Modus um.

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
- [x] **TC-TW-02 [🌐]:** Überprüfung des DSGVO-konformen Karten-Previews. Die Karte wird über den internen Traefik-Proxy (`/srv/api/map/static`) ohne direkte Verbindung zu OpenStreetMap geladen. Das Badge „DSGVO-konform“ ist sichtbar.
- [x] **TC-TW-03 [🌐]:** Klick auf den Button „iCal Kalendereintrag (.ics)“. Eine iCalendar-Datei wird zum Download bereitgestellt.
- [x] **TC-TW-04 [🌐]:** Klick auf „Online-Anmeldung“ bei einem zukünftigen Turnier/Wettbewerb. Der Dialog zur öffentlichen Spieler-Anmeldung (`DlgPublicRegistration`) öffnet sich.
- [x] **TC-TW-05 [🌐]:** Klick auf eine Wettbewerbszeile. Die Anwendung wechselt zur öffentlichen Detailansicht `CompetitionWelcome`.

### 3.2 Turnierverwalter & Admin (👤 / 👑)
- [x] **TC-TW-06 [👤/👑]:** In der Kopfzeile wird zusätzlich ein Button „Zur Turnierverwaltung“ angezeigt, der direkt zur Admin-Ansicht `TourneyInfo` führt.

---

## 4. UseCase: Öffentliche Wettbewerbs-Startseite (`CompetitionWelcome`)

### 4.1 Anonymer Besucher (🌐)
- [x] **TC-CW-01 [🌐]:** Aufrufen von `CompetitionWelcome` mit einer Wettbewerbs-ID. Name, Sportart, Modus, TTR-Bereich und Status werden angezeigt.
- [x] **TC-CW-02 [🌐]:** Überprüfung der Phasentabelle. Laufende und beendete Austragungsphasen werden gelistet.
- [x] **TC-CW-03 [🌐]:** Klick auf „Ergebnisse / Runden anzeigen“. Die Ergebnisse der jeweiligen Phase werden schreibgeschützt geöffnet (`StageAdmin` im View-Modus).
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
- [x] **TC-TI-13 [👤]:** Lokaler Modus – Turnier verlassen: Klick auf „Speichern/Verlassen“ (`BtnSaveLeave`) speichert/synchronisiert das Turnier und zeigt die Meldung „Speichern & Verlassen“ mit dem Speicherort („Speicherort: "lokal" im Browser“, Cloud-Slug bzw. WordPress-Seiten-ID) und nur einem OK-Button; OK führt ohne JSON-Dateidownload zur Startseite. Verlassen der Turnierseite auf anderem Weg (z.B. über Navbar-Logo) öffnet den Dialog `DlgExportTourney` mit dem Titel „Turnier verlassen“ und Hinweistext „Das aktuelle Turnier wird in der Cloud bzw. lokal gespeichert.“. Bietet genau 2 Buttons: „ABBRECHEN“ (schließt den Dialog und verbleibt auf der Seite) und „VERLASSEN“ (speichert/synchronisiert das Turnier, schließt ohne JSON-Dateidownload ab und navigiert zur Startseite).
- [x] **TC-TI-14 [👤]:** Lokaler Modus – Überschreiben-Prüfung beim Export: Existiert bereits eine Datei mit demselben Namen (aus vorherigem Export oder Import), schließt der Klick auf Exportieren nicht sofort, sondern blendet eine Warnung ein („Eine Datei mit diesem Namen existiert bereits. Möchten Sie die vorhandene Datei wirklich überschreiben?“). Der Button wechselt zu „Überschreiben“. Erst nach Bestätigung wird die Datei überschrieben und heruntergeladen. Wird der Dateiname geändert, verschwindet die Warnung automatisch.
- [x] **TC-TI-15 [👤]:** `TourneyNew` – Button „ERSTELLEN“ & Auswahldialog `DlgTourneyNewMode`: Beim Anlegen eines Turniers lautet der Submit-Button „ERSTELLEN“. Klick öffnet den Auswahldialog zur Wahl zwischen lokalem Turnier (`TourneyTargetMode.Local`) und Cloud-Turnier (`TourneyTargetMode.Cloud`). Für nicht angemeldete Benutzer ist die Cloud-Option ausgegraut (`disabled`) mit dem Hinweis „(Anmeldung erforderlich)“.
- [x] **TC-TI-16 [👤]:** `TourneyNew` – Bearbeiten-Modus (`edit`): Beim Bearbeiten eines bestehenden Turniers lautet der Submit-Button „Turnier speichern“ und speichert die Änderungen direkt ohne Anzeige des Modus-Auswahldialogs.

### 5.2 Anonymer Besucher (🌐)
- [x] **TC-TI-08 [🌐]:** Versuch, `TourneyInfo` oder `TourneyAdmin` als unbefugter/anonymer Nutzer direkt per URL aufzurufen. Die Anwendung leitet automatisch auf `TourneyWelcome` um.

---

## 6. UseCase: Wettbewerbs-Verwaltung (`CompetitionInfo`)

### 6.1 Turnierverwalter (👤)
- [x] **TC-CI-01 [👤]:** Aufrufen von `CompetitionInfo`. Zeigt Wettbewerbs-Details, Phasenübersicht und die Teilnehmer-Akkordeon-Tabelle.
- [x] **TC-CI-02 [👤]:** Klick auf „Austragungsphase starten“. Öffnet den Auslosungs-Assistenten (`StageAdmin` / `StageDraw`).
- [x] **TC-CI-03 [👤]:** Klick auf „Wettbewerb bearbeiten“. Erlaubt die Anpassung von TTR-Limits, Altersklassen und Spieltischen.
- [x] **TC-CI-04 [👤]:** Auswahl der Urkunden-Radiobuttons für eine Phase. Markiert die Phase als maßgeblich für den Urkundendruck.
- [x] **TC-CI-05 [👤]:** Klick auf „Wettbewerb beenden“. Der Status wechselt auf Beendet und Bearbeitungsaktionen werden gesperrt.
- [x] **TC-CI-06 [👤]:** Eigenständigen Wettbewerb (`ident == "SIMPLE"`) löschen – Bestätigungsdialog Abbrechen: Klick auf den Löschen-Button öffnet einen Sicherheits-Bestätigungsdialog (`DlgMsgbox`). Klick auf „Nein“ schließt den Dialog und behält den Wettbewerb sowie alle Daten im Speicher unverändert bei.
- [x] **TC-CI-07 [👤]:** Eigenständigen Wettbewerb (`ident == "SIMPLE"`) im lokalen Modus löschen: Klick auf „Löschen“ und Bestätigen mit „Ja“ löscht den lokalen Speicher (`App.demo_tourney`, `App.demo_comps` etc.) sowie sämtliche Browser-Schlüssel (`tourney_is_local_mode`, `tourney_slug`, `tourney_name`, `tourney_ident`, `tourney_last_*`). Navigiert zur Startseite (`MainView`), auf der anschließend keine Status-Zeile für das letzte Turnier mehr erscheint.
- [x] **TC-CI-08 [👤]:** Eigenständigen Wettbewerb (`ident == "SIMPLE"`) im Cloud-Modus löschen: Klick auf „Löschen“ und Bestätigen mit „Ja“ sendet einen `DELETE`-Request an den Server (`/wp-json/tourney/v1/delete`), bereinigt sämtliche Browser-Speicherschlüssel und navigiert zur Startseite (`MainView`), ohne dass eine Status-Zeile verbleibt.
- [x] **TC-CI-09 [👤]:** Nach dem Löschen eines Wettbewerbs (`ident == "SIMPLE"`) startet ein nachfolgender Klick auf „Demo“ direkt, ohne dass die Warnmeldung „Achtung: das aktuelle lokale Turnier wird gelöscht!“ erscheint.

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

## 8. UseCase: Phasen-Verwaltung & Auslosung (`StageAdmin` & `StageDraw`)

### 8.1 Turnierverwalter (👤)
- [x] **TC-SA-01 [👤]:** Aufrufen von `StageAdmin` für eine neue Phase. Wählen des Spielsystems (z. B. Schweizer System, Gruppenphase, KO-System, Jeder-gegen-Jeden).
- [x] **TC-SA-02 [👤]:** Konfigurieren von Gruppenanzahl, Qualifikanten pro Gruppe und Gewinnsätzen (z. B. Best-of-5).
- [x] **TC-SA-03 [👤]:** Ausführen der Auslosung (`StageDraw`). Die Spieler werden gemäß TTR-Setzung oder Zufall auf Gruppen/KO-Pfade verteilt.
- [x] **TC-SA-04 [👤]:** Manuelles Tauschen von Spielern in Gruppen/Runden vor Freigabe der Phase.
- [x] **TC-SA-05 [👤]:** Freigabe der Phase („Phase starten“). Der Spielplan und die Ergebniseingabe werden aktiviert.
- [x] **TC-SA-06 [👤]:** Nachfolgende Phase mit vorangegangenem Rundenturnier (`RoundRobin`): Filterung nach „Obere Hälfte“, „Untere Hälfte“ und „Alle“ sowie Anzeige der Vorrundenplatzierung in der Spalte `prev_stage`.

---

## 9. UseCase: Ergebniseingabe & Spielplan (`StageInput` & `StageResult`)

### 9.1 Turnierverwalter (👤)
- [x] **TC-SI-01 [👤]:** Aufrufen der Ergebniseingabe `StageInput`. Die Ansetzungen der aktuellen Runde / Gruppen werden angezeigt.
- [x] **TC-SI-02 [👤]:** Eingabe von Satzergebnissen (z. B. `11:9, 9:11, 11:8, 11:6`). Das Spielergebnis wird automatisch berechnet (3:1) und der Sieger markiert.
- [x] **TC-SI-03 [👤]:** Zuweisung eines Spieltisches (z. B. „Tisch 4“). Der Tischstatus aktualisiert sich.
- [x] **TC-SI-04 [👤]:** Validierung ungültiger Eingaben (z. B. unvollständige Sätze). Die Anwendung zeigt eine Fehlermeldung und verhindert das Speichern.
- [x] **TC-SI-05 [👤]:** Aufrufen von `StageResult`. Die Gruppentabellen (Spielegewinn, Satzverhältnis, Bälle, Punkte) aktualisieren sich in Echtzeit.
- [x] **TC-SI-07 [👤]:** Im Demo-Modus erscheint in der Kopfzeile von `StageInput` (Card-Title `<h5>`) der Button „DEMO-Ergebnisse“. Ein Klick darauf generiert automatische Zufallsergebnisse für alle ungespielten Begegnungen (inkl. automatischer Siegerpropagation in Folge-K.o.-Runden) und deaktiviert den Button nach Abschluss der Phase.
- [x] **TC-SI-08 [👤]:** Im KO-System (`SingleElimination`) wird für Spiele, bei denen die Teilnehmer noch nicht feststehen, der Button für den Schiedsrichterzettel angezeigt. Auf dem erzeugten Zettel bleiben die Spielernamen leer für manuelle Eintragungen. Bei Spielen mit Freilos wird kein Schiedsrichterzettel-Button angezeigt.
- [x] **TC-SI-09 [👤]:** Im Gruppen-Spielsystem (`Groups`) öffnet der Schiedsrichterzettel-Button einer Spielzeile in `StageInput` die Seite `StageScoreSheet` ausschließlich mit dem ausgewählten Spiel (genau 1 Zettel in der Vorschau, passender Titel und Einzelzettel-Druckbutton) statt aller Zettel der Gruppe oder Runde.

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
