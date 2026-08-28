# Manuelle & Automatisierte Testfall-Checkliste für Playdemo / Turnier-Service

Diese Checkliste dient der qualitätssichernden Abnahme der Benutzeroberfläche und Geschäftslogik der **Playdemo / Turnier-Service Webanwendung**.

Die Testfälle sind nach **UseCases / Seiten** strukturiert und berücksichtigen die drei primären Benutzerrollen:
- 🌐 **Nicht eingeloggt (Anonymer Besucher)**
- 👤 **TourneyAdmin (Turnierverwalter / Turnierorganisator)**
- 👑 **TourneyMaster / WP-Admin (Systemadministrator / Plattformverwalter)**

---

## 1. UseCase: Startseite & Allgemeine Navigation (`MainView`)

### 1.1 Anonymer Besucher (🌐)
- [x] **TC-MV-01 [🌐]:** Aufrufen der Startseite (`/`). Die Seite lädt ohne Fehler, das Logo, die Schnellstart-Buttons („Guthaben / Vollversion“, „Schnellstart“) und der Such-Button („Turniersuche“) sind sichtbar.
- [x] **TC-MV-02 [🌐]:** Klick auf den Button „Turniersuche“. Die Anwendung wechselt ohne Seiten-Reload zum UseCase `MainSearch`.
- [x] **TC-MV-03 [🌐]:** Klick auf „Guthaben / Vollversion“ oder „Schnellstart“ ohne Anmeldung. Es erscheint ein Demo-Modus Dialog (`promptDemoMode`), der zur Wahl zwischen Demo-Modus oder Anmeldung auffordert.
- [x] **TC-MV-04 [🌐]:** Aufrufen statischer Seiten über den Footer (z. B. AGB, Datenschutz, Impressum, Kontakt, Preise). Die entsprechenden HTML-Inhalte werden korrekt geladen.
- [x] **TC-MV-05 [🌐]:** Umschalten der Sprache in der Navigationsleiste (Deutsch / Englisch). Die UI-Texte und statischen Seiten passen sich der gewählten Sprache an.

### 1.2 Turnierverwalter (👤)
- [x] **TC-MV-06 [👤]:** Aufrufen der Startseite im angemeldeten Zustand. In der Navigationsleiste werden der Benutzername und der Abmelden-Button angezeigt.
- [x] **TC-MV-07 [👤]:** Klick auf „Guthaben / Vollversion“. Öffnet direkt das Formular zur Erstellung eines neuen Turniers (`TourneyNew`).
- [x] **TC-MV-08 [👤]:** Klick auf „Schnellstart“. Erstellt direkt ein vereinfachtes Einzelturnier im Schnellstart-Modus (`doQuickStart`).

### 1.3 Systemadministrator (👑)
- [x] **TC-MV-09 [👑]:** Aufrufen der Startseite als `administrator` oder `tourney_master`. In der Navigationsleiste erscheint zusätzlich der Button „Management“.
- [x] **TC-MV-10 [👑]:** Klick auf den Button „Management“. Die Anwendung wechselt direkt zur Admin-Verwaltungskonsole (`Management`).

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

---

## 9. UseCase: Ergebniseingabe & Spielplan (`StageInput` & `StageResult`)

### 9.1 Turnierverwalter (👤)
- [x] **TC-SI-01 [👤]:** Aufrufen der Ergebniseingabe `StageInput`. Die Ansetzungen der aktuellen Runde / Gruppen werden angezeigt.
- [x] **TC-SI-02 [👤]:** Eingabe von Satzergebnissen (z. B. `11:9, 9:11, 11:8, 11:6`). Das Spielergebnis wird automatisch berechnet (3:1) und der Sieger markiert.
- [x] **TC-SI-03 [👤]:** Zuweisung eines Spieltisches (z. B. „Tisch 4“). Der Tischstatus aktualisiert sich.
- [x] **TC-SI-04 [👤]:** Validierung ungültiger Eingaben (z. B. unvollständige Sätze). Die Anwendung zeigt eine Fehlermeldung und verhindert das Speichern.
- [x] **TC-SI-05 [👤]:** Aufrufen von `StageResult`. Die Gruppentabellen (Spielegewinn, Satzverhältnis, Bälle, Punkte) aktualisieren sich in Echtzeit.

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
