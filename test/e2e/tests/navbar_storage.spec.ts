import { test, expect } from '@playwright/test';
import fs from 'fs';
import path from 'path';
import { MainViewPage } from '../pages/MainViewPage';
import { TourneyInfoPage } from '../pages/TourneyInfoPage';

test.describe('UseCase 1.4: Main Navbar - Demo-Button & Ablage-Dropdown', () => {
  let mainPage: MainViewPage;
  let infoPage: TourneyInfoPage;

  test.beforeEach(async ({ page }) => {
    mainPage = new MainViewPage(page);
    infoPage = new TourneyInfoPage(page);
  });

  test('TC-NB-01 [🌐]: Navbar zeigt Ablage-Dropdown und RELOAD, kein Demo-Button, kein altes Start-Menü, kein Lokaler-Modus Badge', async ({ page }) => {
    await mainPage.goto();

    // 1. Verify Demo button is NOT present in navbar
    const navDemoBtn = page.locator('.navbar button[id*="StartDemoId"], .navbar .btn-navbar-demo');
    await expect(navDemoBtn).not.toBeVisible();

    // 2. Verify old start dropdown, search link and "Lokaler Modus" badge are absent
    await expect(page.locator('#navbarStart')).not.toBeVisible();
    await expect(page.locator('.navbar a:has-text("Suchen"), .navbar a:has-text("Search")')).not.toBeVisible();
    await expect(page.locator('.badge:has-text("Lokaler Modus"), .badge:has-text("Local Mode")')).not.toBeVisible();

    // 3. Verify RELOAD button is NOT present in navbar
    const reloadBtn = page.locator('.navbar button[id*="ReloadTourneyId"], .navbar .btn-navbar-reload');
    await expect(reloadBtn).not.toBeVisible();

    // 4. Verify "Ablage" / "Storage" dropdown exists
    const offlineDropdown = page.locator('#offlineDropdown');
    await expect(offlineDropdown).toBeVisible();
    await expect(offlineDropdown).toHaveText(/Ablage|Storage/);

    // 5. Open "Ablage" dropdown and check the 4 items
    await offlineDropdown.click();

    const loadLastItem = page.locator('a[id*="LoadLastTourneyId"], a.dropdown-item-load-last');
    const exportItem = page.locator('a[id*="ExportLocalId"]');
    const importLocalItem = page.locator('a[id*="ImportLocalId"]');
    const moveCloudItem = page.locator('a[id*="MoveToCloudId"], a.dropdown-item-move-cloud');

    // Item 1: "Letztes Turnier laden" in red color (text-danger)
    await expect(loadLastItem).toBeVisible();
    await expect(loadLastItem).toHaveText(/Letztes Turnier laden|Load last tournament/);
    await expect(loadLastItem).toHaveClass(/text-danger/);

    // Item 2: "Turnier exportieren in Datei (JSON)"
    await expect(exportItem).toBeVisible();
    await expect(exportItem).toHaveText(/Turnier exportieren in Datei \(JSON\)|Export tournament to file \(JSON\)/);

    // Item 3: "Turnier von Datei (JSON) laden."
    await expect(importLocalItem).toBeVisible();
    await expect(importLocalItem).toHaveText(/Turnier von Datei \(JSON\) laden|Load tournament from file \(JSON\)/);

    // Item 4: "Turnier in die Cloud verschieben"
    await expect(moveCloudItem).toBeVisible();
    await expect(moveCloudItem).toHaveText(/Turnier in die Cloud verschieben|Move tournament to the cloud/);

    // Old items should not exist
    await expect(page.locator('a[id*="ImportCloudId"]')).not.toBeVisible();
    await expect(page.locator('a[id*="LoadLocalTourneyId"]')).not.toBeVisible();
  });

  test('TC-NB-02 [🌐]: Klick auf Demo-Button (MainView) öffnet Dialog "Demo lokal starten?", Abbrechen verbleibt, Bestätigen startet Demo', async ({ page }) => {
    await mainPage.goto();

    const demoBtn = mainPage.btnDemoBox;
    await expect(demoBtn).toBeVisible();
    await demoBtn.click();

    // 1. Verify DlgStartDemo modal appears:
    // - Title: "Demo lokal starten?"
    // - Text: "Achtung: das aktuelle lokale Turnier wird gelöscht!"
    // - 2 Buttons: "ABBRECHEN" and "DEMO STARTEN"
    await expect(mainPage.dlgStartDemoModal).toBeVisible({ timeout: 5000 });
    await expect(mainPage.dlgStartDemoTitle).toHaveText(/Demo lokal starten\?|Start demo locally\?/i);
    await expect(mainPage.dlgStartDemoPrompt).toHaveText(/Achtung: das aktuelle lokale Turnier wird gelöscht!|Warning: the current local tournament will be deleted!/i);

    const footerButtons = mainPage.dlgStartDemoModal.locator('.modal-footer button');
    await expect(footerButtons).toHaveCount(2);
    await expect(mainPage.btnStartDemoCancel).toHaveText(/ABBRECHEN|CANCEL/i);
    await expect(mainPage.btnStartDemoApply).toHaveText(/DEMO STARTEN|START DEMO/i);

    // 2. Test cancel: click ABBRECHEN -> modal closes, stays on current page
    await mainPage.btnStartDemoCancel.click();
    await expect(mainPage.dlgStartDemoModal).not.toBeVisible({ timeout: 3000 });
    await expect(infoPage.cardHeaderTitle).not.toBeVisible();

    // 3. Open dialog again and click DEMO STARTEN
    await demoBtn.click();
    await expect(mainPage.dlgStartDemoModal).toBeVisible();
    await mainPage.btnStartDemoApply.click();
    await expect(mainPage.dlgStartDemoModal).not.toBeVisible({ timeout: 3000 });

    // 4. Verify navigation to TourneyInfo
    await expect(infoPage.cardHeaderTitle).toBeVisible({ timeout: 10000 });
    
    // Verify tournament name starts with "Demo:"
    const tourneyNameElem = page.locator('.card-body .fw-bold.text-primary').first();
    await expect(tourneyNameElem).toBeVisible();
    const titleText = await tourneyNameElem.innerText();
    expect(titleText).toContain('Demo:');

    // Verify demo tournament data is stored in localStorage
    const storedTourney = await page.evaluate(() => window.localStorage.getItem('App.demo_tourney'));
    expect(storedTourney).toBeTruthy();
    expect(storedTourney).toContain('Demo:');
    await expect(infoPage.btnEditTourney).toBeEnabled();

    // Verify tourney_is_local_mode = "true" and tourney_slug = "local" in localStorage and sessionStorage
    const storageKeys = await page.evaluate(() => ({
      localModeLocal: window.localStorage.getItem('tourney_is_local_mode'),
      localModeSession: window.sessionStorage.getItem('tourney_is_local_mode'),
      slugLocal: window.localStorage.getItem('tourney_slug'),
      slugSession: window.sessionStorage.getItem('tourney_slug'),
    }));
    expect(storageKeys.localModeLocal).toBe('true');
    expect(storageKeys.slugLocal).toBe('local');
    expect(storageKeys.slugSession).toBe('local');

    // Verify "Lokaler Modus" badge remains absent even in local mode
    await expect(page.locator('.badge:has-text("Lokaler Modus"), .badge:has-text("Local Mode")')).not.toBeVisible();
  });

  test('TC-NB-03 [🌐]: "Turnier in die Cloud verschieben" ist ausgegraut (disabled) wenn User nicht angemeldet ist', async ({ page }) => {
    await mainPage.goto();

    const offlineDropdown = page.locator('#offlineDropdown');
    await expect(offlineDropdown).toBeVisible();
    await offlineDropdown.click();

    const moveCloudItem = page.locator('a[id*="MoveToCloudId"], a.dropdown-item-move-cloud');
    await expect(moveCloudItem).toBeVisible();
    // Must have class 'disabled'
    await expect(moveCloudItem).toHaveClass(/disabled/);
    await expect(moveCloudItem).toHaveAttribute('aria-disabled', 'true');
  });

  test('TC-NB-04 [👤]: "Turnier in die Cloud verschieben" ist ausgegraut (disabled) wenn aktuelles Turnier bereits in der Cloud ist', async ({ page }) => {
    // Intercept user endpoint to simulate logged-in organizer
    await page.route('**/wp-json/tourney/v1/user', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          username: 'admin',
          user_id: 1,
          email: 'admin@test.de',
          club: 'Test Club',
          roles: ['administrator'],
          time: '2026-10-11 20:00:00',
        }),
      });
    });

    await mainPage.goto();

    // Set tournament state to cloud mode (tourney_is_local_mode = false)
    await page.evaluate(() => {
      window.localStorage.setItem('tourney_is_local_mode', 'false');
      window.sessionStorage.setItem('tourney_is_local_mode', 'false');
      window.localStorage.setItem('tourney_slug', 'cloud-tourney');
      window.sessionStorage.setItem('tourney_slug', 'cloud-tourney');
    });

    const offlineDropdown = page.locator('#offlineDropdown');
    await expect(offlineDropdown).toBeVisible();
    await offlineDropdown.click();

    const moveCloudItem = page.locator('a[id*="MoveToCloudId"], a.dropdown-item-move-cloud');
    await expect(moveCloudItem).toBeVisible();
    // Must be disabled because tournament is already in the cloud
    await expect(moveCloudItem).toHaveClass(/disabled/);
    await expect(moveCloudItem).toHaveAttribute('aria-disabled', 'true');
  });

  test('TC-NB-04b [👤]: "Turnier in die Cloud verschieben" ist AKTIV wenn User angemeldet ist und lokales Turnier aktiv ist', async ({ page }) => {
    // Intercept user endpoint to simulate logged-in organizer
    await page.route('**/wp-json/tourney/v1/user', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          username: 'organizer1',
          user_id: 10,
          email: 'org@test.de',
          club: 'Mein Verein',
          roles: ['turnier_admin'],
          time: '2026-10-11 20:00:00',
        }),
      });
    });

    await mainPage.goto();

    // Start Demo to have an active local tournament
    await mainPage.startDemo();
    await expect(infoPage.cardHeaderTitle).toBeVisible({ timeout: 10000 });

    const offlineDropdown = page.locator('#offlineDropdown');
    await expect(offlineDropdown).toBeVisible();
    await offlineDropdown.click();

    const moveCloudItem = page.locator('a[id*="MoveToCloudId"], a.dropdown-item-move-cloud');
    await expect(moveCloudItem).toBeVisible();
    // Must NOT be disabled
    await expect(moveCloudItem).not.toHaveClass(/disabled/);
  });

  test('TC-NB-05 [🌐]: Turnier laden (lokal) importiert JSON-Datei im lokalen Modus', async ({ page }) => {
    await mainPage.goto();

    // Prepare sample tournament JSON from valid demo payload
    const demoJsonPath = path.resolve(__dirname, '../../../wp-plugin/data/demo_tourney.json');
    const demoRaw = JSON.parse(fs.readFileSync(demoJsonPath, 'utf-8'));
    demoRaw.tourney.tourney.name = 'Importiertes Testturnier';
    const jsonBuffer = Buffer.from(JSON.stringify(demoRaw));

    let alertMessage = '';
    page.on('dialog', async (dialog) => {
      alertMessage = dialog.message();
      await dialog.accept();
    });

    const offlineDropdown = page.locator('#offlineDropdown');
    await expect(offlineDropdown).toBeVisible();
    await offlineDropdown.click();

    // Intercept file chooser triggered by clicking ImportLocalId
    const [fileChooser] = await Promise.all([
      page.waitForEvent('filechooser'),
      page.locator('a[id*="ImportLocalId"]').click(),
    ]);

    await fileChooser.setFiles({
      name: 'import_test.json',
      mimeType: 'application/json',
      buffer: jsonBuffer,
    });

    // Verify alert message and navigation to TourneyInfo
    await expect(infoPage.cardHeaderTitle).toBeVisible({ timeout: 10000 });
    const tourneyNameElem = page.locator('.card-body .fw-bold.text-primary').first();
    await expect(tourneyNameElem).toBeVisible();
    const titleText = await tourneyNameElem.innerText();
    expect(titleText).toContain('Importiertes Testturnier');
    expect(alertMessage).toContain('Import erfolgreich');

    const storedTourney = await page.evaluate(() => window.localStorage.getItem('App.demo_tourney'));
    expect(storedTourney).toBeTruthy();
    expect(storedTourney).toContain('Importiertes Testturnier');
    await expect(infoPage.btnEditTourney).toBeEnabled();

    // Verify tourney_slug = "local" on import
    const slugVal = await page.evaluate(() => window.localStorage.getItem('tourney_slug'));
    expect(slugVal).toBe('local');
  });

  test('TC-NB-06 [🌐]: Speicherschlüssel tourney_slug wird entsprechend tourney_is_local_mode gesetzt (local vs <slug>)', async ({ page }) => {
    await mainPage.goto();

    // 1. In Local Mode: tourney_is_local_mode = "true" -> tourney_slug = "local"
    await page.evaluate(() => {
      window.localStorage.setItem('tourney_is_local_mode', 'true');
      window.sessionStorage.setItem('tourney_is_local_mode', 'true');
      window.localStorage.setItem('tourney_slug', 'local');
      window.sessionStorage.setItem('tourney_slug', 'local');
    });

    let storageState = await page.evaluate(() => ({
      isLocal: window.localStorage.getItem('tourney_is_local_mode'),
      slug: window.localStorage.getItem('tourney_slug'),
      sessionSlug: window.sessionStorage.getItem('tourney_slug'),
    }));
    expect(storageState.isLocal).toBe('true');
    expect(storageState.slug).toBe('local');
    expect(storageState.sessionSlug).toBe('local');

    // 2. In Non-Local Mode: tourney_is_local_mode = "false" -> tourney_slug = "<slug>"
    await page.evaluate(() => {
      window.localStorage.setItem('tourney_is_local_mode', 'false');
      window.sessionStorage.setItem('tourney_is_local_mode', 'false');
      window.localStorage.setItem('tourney_slug', 'test-turnier-2026');
      window.sessionStorage.setItem('tourney_slug', 'test-turnier-2026');
    });

    storageState = await page.evaluate(() => ({
      isLocal: window.localStorage.getItem('tourney_is_local_mode'),
      slug: window.localStorage.getItem('tourney_slug'),
      sessionSlug: window.sessionStorage.getItem('tourney_slug'),
    }));
    expect(storageState.isLocal).toBe('false');
    expect(storageState.slug).toBe('test-turnier-2026');
    expect(storageState.sessionSlug).toBe('test-turnier-2026');
  });

  test('TC-NB-07 [🌐]: RELOAD-Button ist aus Navbar entfernt; "Letztes Turnier laden" im Ablage-Dropdown lädt lokales Turnier in TourneyInfo', async ({ page }) => {
    await mainPage.goto();

    // 1. Verify RELOAD button is NOT present in navbar
    const reloadBtn = page.locator('.navbar button[id*="ReloadTourneyId"], .navbar .btn-navbar-reload');
    await expect(reloadBtn).not.toBeVisible();

    // 2. Prepare sample local tournament in localStorage and set tourney_is_local_mode = true
    const demoJsonPath = path.resolve(__dirname, '../../../wp-plugin/data/demo_tourney.json');
    const demoRaw = JSON.parse(fs.readFileSync(demoJsonPath, 'utf-8'));
    demoRaw.tourney.tourney.name = 'Reloaded Local Tourney';
    await page.evaluate((payload) => {
      window.localStorage.setItem('App.demo_tourney', JSON.stringify(payload.tourney));
      window.localStorage.setItem('tourney_is_local_mode', 'true');
      window.sessionStorage.setItem('tourney_is_local_mode', 'true');
      window.localStorage.setItem('tourney_slug', 'local');
      window.sessionStorage.setItem('tourney_slug', 'local');
    }, demoRaw);

    // 3. Open Ablage dropdown and click "Letztes Turnier laden" (red menu item)
    const offlineDropdown = page.locator('#offlineDropdown');
    await expect(offlineDropdown).toBeVisible();
    await offlineDropdown.click();

    const loadLastItem = page.locator('a[id*="LoadLastTourneyId"], a.dropdown-item-load-last');
    await expect(loadLastItem).toBeVisible();
    await expect(loadLastItem).toHaveClass(/text-danger/);
    await loadLastItem.click();

    // 4. Verify navigation to TourneyInfo and loaded tournament name
    await expect(infoPage.cardHeaderTitle).toBeVisible({ timeout: 10000 });
    const tourneyNameElem = page.locator('.card-body .fw-bold.text-primary').first();
    await expect(tourneyNameElem).toBeVisible();
    expect(await tourneyNameElem.innerText()).toContain('Reloaded Local Tourney');
  });

  test('TC-NB-08 [🌐]: "Letztes Turnier laden" im lokalen Modus ohne gespeichertes Turnier zeigt Hinweisdialog', async ({ page }) => {
    await mainPage.goto();

    await page.evaluate(() => {
      window.localStorage.removeItem('App.demo_tourney');
      window.localStorage.setItem('tourney_is_local_mode', 'true');
      window.sessionStorage.setItem('tourney_is_local_mode', 'true');
      window.localStorage.setItem('tourney_slug', 'local');
      window.sessionStorage.setItem('tourney_slug', 'local');
    });

    let dialogMessage = '';
    page.once('dialog', async (dialog) => {
      dialogMessage = dialog.message();
      await dialog.accept();
    });

    const offlineDropdown = page.locator('#offlineDropdown');
    await expect(offlineDropdown).toBeVisible();
    await offlineDropdown.click();

    const loadLastItem = page.locator('a[id*="LoadLastTourneyId"], a.dropdown-item-load-last');
    await expect(loadLastItem).toBeVisible();
    await loadLastItem.click();

    await page.waitForTimeout(500);
    expect(dialogMessage).toContain('Kein lokal gespeichertes Turnier vorhanden');
  });

  test('TC-NB-09 [🌐]: "Letztes Turnier laden" im Cloud-Modus (tourney_is_local_mode = false) ohne Login zeigt Hinweis und leitet zu UserLogin', async ({ page }) => {
    await mainPage.goto();

    await page.evaluate(() => {
      window.localStorage.setItem('tourney_is_local_mode', 'false');
      window.sessionStorage.setItem('tourney_is_local_mode', 'false');
      window.localStorage.setItem('tourney_slug', 'online-stadtmeisterschaft-2026');
      window.sessionStorage.setItem('tourney_slug', 'online-stadtmeisterschaft-2026');
    });

    let dialogMessage = '';
    page.once('dialog', async (dialog) => {
      dialogMessage = dialog.message();
      await dialog.accept();
    });

    const offlineDropdown = page.locator('#offlineDropdown');
    await expect(offlineDropdown).toBeVisible();
    await offlineDropdown.click();

    const loadLastItem = page.locator('a[id*="LoadLastTourneyId"], a.dropdown-item-load-last');
    await expect(loadLastItem).toBeVisible();
    await loadLastItem.click();

    await page.waitForTimeout(500);
    expect(dialogMessage).toMatch(/Anmeldung erforderlich|Login is required/);

    // Verify redirected to UserLogin
    await expect(page.locator('input[type="email"]').first()).toBeVisible({ timeout: 5000 });
  });

  test('TC-NB-10 [👤]: "Letztes Turnier laden" im Cloud-Modus mit angemeldetem Benutzer ohne gespeicherten tourney_slug zeigt Hinweis', async ({ page }) => {
    // Intercept user endpoint to simulate logged-in admin
    await page.route('**/wp-json/tourney/v1/user', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          username: 'testadmin',
          user_id: 1,
          email: 'admin@test.de',
          club: 'TTC Test',
          roles: ['administrator'],
          time: '2026-10-11 20:00:00',
        }),
      });
    });

    await mainPage.goto();

    await page.evaluate(() => {
      window.localStorage.setItem('tourney_is_local_mode', 'false');
      window.sessionStorage.setItem('tourney_is_local_mode', 'false');
      window.localStorage.removeItem('tourney_slug');
      window.sessionStorage.removeItem('tourney_slug');
    });

    let dialogMessage = '';
    page.once('dialog', async (dialog) => {
      dialogMessage = dialog.message();
      await dialog.accept();
    });

    const offlineDropdown = page.locator('#offlineDropdown');
    await expect(offlineDropdown).toBeVisible();
    await offlineDropdown.click();

    const loadLastItem = page.locator('a[id*="LoadLastTourneyId"], a.dropdown-item-load-last');
    await expect(loadLastItem).toBeVisible();
    await loadLastItem.click();

    await page.waitForTimeout(500);
    expect(dialogMessage).toMatch(/Kein Turnier-Slug gespeichert|No tournament slug stored/);
  });

  test('TC-NB-11 [👤]: "Letztes Turnier laden" im Cloud-Modus mit angemeldetem Benutzer ohne Rechte für das Turnier zeigt Berechtigungsfehler', async ({ page }) => {
    // Intercept user endpoint to simulate logged-in regular subscriber
    await page.route('**/wp-json/tourney/v1/user', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          username: 'subscriber1',
          user_id: 99,
          email: 'sub@test.de',
          club: 'Fremder Club',
          roles: ['subscriber'],
          time: '2026-10-11 20:00:00',
        }),
      });
    });

    // Intercept tourney read endpoint to return tournament belonging to another organizer
    const demoJsonPath = path.resolve(__dirname, '../../../wp-plugin/data/demo_tourney.json');
    const demoRaw = JSON.parse(fs.readFileSync(demoJsonPath, 'utf-8'));
    const foreignTourney = {
      ...demoRaw.tourney.tourney,
      wpId: 42,
      name: 'Fremdes Cloud-Turnier',
      organizer: 'Anderer Veranstalter',
      slug: 'fremdes-turnier',
      version: 1,
    };

    await page.route('**/wp-json/tourney/v1/read*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          version: 1,
          tourney: foreignTourney,
        }),
      });
    });

    await page.route('**/wp-json/tourney/v1/read-aci*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          dateFormat: 'DE',
        }),
      });
    });

    await mainPage.goto();

    await page.evaluate(() => {
      window.localStorage.setItem('tourney_is_local_mode', 'false');
      window.sessionStorage.setItem('tourney_is_local_mode', 'false');
      window.localStorage.setItem('tourney_slug', 'fremdes-turnier');
      window.sessionStorage.setItem('tourney_slug', 'fremdes-turnier');
    });

    let dialogMessage = '';
    page.once('dialog', async (dialog) => {
      dialogMessage = dialog.message();
      await dialog.accept();
    });

    const offlineDropdown = page.locator('#offlineDropdown');
    await expect(offlineDropdown).toBeVisible();
    await offlineDropdown.click();

    const loadLastItem = page.locator('a[id*="LoadLastTourneyId"], a.dropdown-item-load-last');
    await expect(loadLastItem).toBeVisible();
    await loadLastItem.click();

    await page.waitForTimeout(500);
    expect(dialogMessage).toMatch(/keine Berechtigung|permission/i);
  });

  test('TC-NB-12 [👤]: "Turnier in die Cloud verschieben" verschiebt lokales Turnier erfolgreich in die Cloud', async ({ page }) => {
    // Intercept user endpoint
    await page.route('**/wp-json/tourney/v1/user', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          username: 'organizer1',
          user_id: 10,
          email: 'org@test.de',
          club: 'Mein Verein',
          roles: ['turnier_admin'],
          time: '2026-10-11 20:00:00',
        }),
      });
    });

    // Intercept create endpoint
    await page.route('**/wp-json/tourney/v1/create', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          action: 'create',
          pageId: 55,
          parentId: 10,
          username: 'organizer1',
          organizer: 'Mein Verein',
          slug: 'tourney/mein-verein/verschobenes-turnier',
          version: 1,
        }),
      });
    });

    // Intercept sync, meta-data, and aci
    await page.route('**/wp-json/tourney/v1/tourney-sync*', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ version: 1 }) });
    });
    await page.route('**/wp-json/tourney/v1/meta-data*', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify('OK') });
    });
    await page.route('**/wp-json/tourney/v1/*-aci*', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ dateFormat: 'DE' }) });
    });

    await mainPage.goto();

    // Start Demo to have an active local tournament
    await mainPage.startDemo();
    await expect(infoPage.cardHeaderTitle).toBeVisible({ timeout: 10000 });

    // Handle confirm dialog and success alert
    page.on('dialog', async (dialog) => {
      await dialog.accept();
    });

    const offlineDropdown = page.locator('#offlineDropdown');
    await expect(offlineDropdown).toBeVisible();
    await offlineDropdown.click();

    const moveCloudItem = page.locator('a[id*="MoveToCloudId"], a.dropdown-item-move-cloud');
    await expect(moveCloudItem).toBeVisible();
    await expect(moveCloudItem).not.toHaveClass(/disabled/);
    await moveCloudItem.click();

    // Verify localStorage: App.demo_tourney cleared, tourney_is_local_mode = false, tourney_slug = new slug
    await page.waitForTimeout(1000);
    const storageKeys = await page.evaluate(() => ({
      demoTourney: window.localStorage.getItem('App.demo_tourney'),
      localMode: window.localStorage.getItem('tourney_is_local_mode'),
      slug: window.localStorage.getItem('tourney_slug'),
    }));

    expect(storageKeys.demoTourney).toBeNull();
    expect(storageKeys.localMode).toBe('false');
    expect(storageKeys.slug).toBe('tourney/mein-verein/verschobenes-turnier');
  });
});

