import { test, expect } from '@playwright/test';
import { UserRegistrationPage } from '../pages/UserRegistrationPage';

/**
 * Fehlerdialog (DlgMsgbox.showError): zeigt nur den Fehlertext, Code und CallStack erst über "Details".
 * Ausgelöst über eine per Mock fehlschlagende Registrierung.
 */
test.describe('Fehlerdialog mit Details', () => {
  const modal = (page: import('@playwright/test').Page) => page.locator('#DlgMsgbox_ModalId');

  /** Lässt die Registrierung mit der angegebenen Fehlerantwort scheitern und öffnet so den Fehlerdialog. */
  async function triggerError(page: import('@playwright/test').Page, body: object) {
    await page.route('**/wp-json/tourney/v1/auth/register', route =>
      route.fulfill({ status: 403, contentType: 'application/json', body: JSON.stringify(body) }));
    const regPage = new UserRegistrationPage(page);
    await regPage.goto();
    await regPage.register({ email: `fehler_${Date.now()}@example.com`, isAdmin: false });
    await expect(modal(page)).toBeVisible({ timeout: 10000 });
  }

  test('TC-ERR-01 [🌐]: WordPress-Fehler ohne Übersetzung zeigt den Server-Text, Details mit Code und CallStack', async ({ page }) => {
    await triggerError(page, { code: 'rest_cookie_invalid_nonce', message: 'Die Cookie-Prüfung ist fehlgeschlagen', data: { status: 403 } });
    const dlg = modal(page);

    await expect(dlg.locator('.modal-title')).toHaveText(/^(Fehler|Error)$/);
    const body = dlg.locator('#DlgMsgbox_BodyId');
    await expect(body).toHaveText('Die Cookie-Prüfung ist fehlgeschlagen');
    await expect(body).not.toContainText('AppError');
    await expect(body).not.toContainText('callStack');

    // "Details" steht vor "Schließen", Details sind zunächst verborgen
    const buttons = dlg.locator('.modal-footer button');
    await expect(buttons).toHaveCount(2);
    await expect(buttons.nth(0)).toHaveText('Details');
    await expect(buttons.nth(1)).toHaveText(/SCHLIESSEN|CLOSE/);
    const details = dlg.locator('#DlgMsgbox_DetailsId');
    await expect(details).toBeHidden();

    await buttons.nth(0).click();
    await expect(details).toBeVisible();
    await expect(details).toContainText('Code: rest_cookie_invalid_nonce');
    await expect(details).toContainText('CallStack: register');

    await buttons.nth(0).click();
    await expect(details).toBeHidden();

    await buttons.nth(1).click();
    await expect(dlg).toBeHidden();
  });

  test('TC-ERR-02 [🌐]: Übersetzter Fehler zeigt den Text mit eingesetzten Inserts', async ({ page }) => {
    await triggerError(page, { msgCode: 'err00009.ajax.get', in1: '/auth/register', in2: 'Status 403', callStack: 'register:' });
    const dlg = modal(page);

    await expect(dlg.locator('#DlgMsgbox_BodyId')).not.toContainText('AppError');
    await expect(dlg.locator('#DlgMsgbox_BodyId')).toContainText('/auth/register');
    await expect(dlg.locator('#DlgMsgbox_BodyId')).toContainText('Status 403');
    await dlg.locator('#DlgMsgbox_DetailsBtnId').click();
    await expect(dlg.locator('#DlgMsgbox_DetailsId')).toContainText('Code: err00009.ajax.get');
  });
});
