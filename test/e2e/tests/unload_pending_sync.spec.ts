import { test, expect, Page } from '@playwright/test';
import { CompetitionInfoPage } from '../pages/CompetitionInfoPage';
import { MainViewPage } from '../pages/MainViewPage';

const compName = 'Cloud Unload Sync';
const cloudSlug = 'cloud-unload-sync-test';

/**
 * Mocks the WordPress API for a logged-in administrator with a cloud competition (status RUN),
 * opens it in CompetitionInfo and returns the bodies of all competitions-sync requests.
 */
async function openCloudCompetition(page: Page): Promise<string[]> {
  const competitions = [{
    id: 1, name: compName, typ: 'SINGLE', category: 'TT', startDate: '2026-10-14 10:00:00',
    status: 'RUN', activ: true, deleted: false, pants1Stage: []
  }];
  const tourney = {
    wpId: 8889, name: compName, organizer: 'admin', startDate: 20261014, endDate: 20261014,
    ident: 'SIMPLE', category: 'TT', slug: cloudSlug, clubs: [], players: [], competitions, stages: []
  };
  const json = (body: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

  await page.route('**/wp-json/tourney/v1/user', route => route.fulfill(json({
    username: 'admin', user_id: 1, email: 'admin@test.de', club: 'Test Club',
    roles: ['administrator'], time: '2026-10-14 10:00:00',
  })));
  await page.route('**/wp-json/tourney/v1/read*', route => route.fulfill(json({ version: 1, tourney })));
  await page.route('**/wp-json/tourney/v1/competitions*', route => route.fulfill(json({ competitions })));
  await page.route('**/wp-json/tourney/v1/clubs*', route => route.fulfill(json({ clubs: [], version: 1 })));
  await page.route('**/wp-json/tourney/v1/players*', route => route.fulfill(json({ players: [], version: 1 })));
  await page.route('**/wp-json/tourney/v1/stages*', route => route.fulfill(json({ stages: [], version: 1 })));
  await page.route('**/wp-json/tourney/v1/*-aci*', route => route.fulfill(json({ dateFormat: 'DE' })));

  const syncBodies: string[] = [];
  await page.route('**/wp-json/tourney/v1/competitions-sync*', async route => {
    syncBodies.push(route.request().postData() ?? '');
    await route.fulfill(json({ success: true }));
  });

  await page.goto('/');
  await page.evaluate(({ name, slug }) => {
    window.localStorage.removeItem('App.demo_tourney');
    for (const s of [window.localStorage, window.sessionStorage]) {
      s.setItem('tourney_is_local_mode', 'false');
      s.setItem('tourney_slug', slug);
      s.setItem('tourney_name', name);
      s.setItem('tourney_ident', 'SIMPLE');
    }
  }, { name: compName, slug: cloudSlug });

  const mainView = new MainViewPage(page);
  await mainView.goto();
  await mainView.btnStatusLoadLast.click();
  await expect(new CompetitionInfoPage(page).cardHeaderTitle).toBeVisible({ timeout: 10000 });
  return syncBodies;
}

test.describe('Ausstehende Synchronisation beim Verlassen der Seite (beforeunload)', () => {

  test('TC-UL-01 [👤]: Verlassen innerhalb der Debounce-Zeit zeigt "Seite verlassen?" und der Sync wird trotzdem gesendet', async ({ page }) => {
    const syncBodies = await openCloudCompetition(page);
    const compInfoPage = new CompetitionInfoPage(page);

    const dialogPromise = page.waitForEvent('dialog');
    // Change the competition and leave immediately, before the 800 ms debounce fires
    await compInfoPage.btnToggleStatus.click();
    await page.close({ runBeforeUnload: true });

    const dialog = await dialogPromise;
    expect(dialog.type()).toBe('beforeunload');
    // Stay on the page: the flushed sync must still reach the server
    await dialog.dismiss();

    // Without stages updateCompetition resets the status to CFG, so only check that the change arrives
    await expect.poll(() => syncBodies.length).toBe(1);
    expect(syncBodies[0]).toContain(compName);
    expect(page.isClosed()).toBe(false);
  });

  test('TC-UL-02 [👤]: Verlassen ohne ausstehende Änderungen zeigt keinen Dialog', async ({ page }) => {
    await openCloudCompetition(page);

    let dialogShown = false;
    page.on('dialog', async dialog => { dialogShown = true; await dialog.dismiss(); });
    await page.close({ runBeforeUnload: true });

    await expect.poll(() => page.isClosed()).toBe(true);
    expect(dialogShown).toBe(false);
  });
});
