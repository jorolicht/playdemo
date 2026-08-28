import { Page, Locator, expect } from '@playwright/test';

export class TourneyAdminPage {
  readonly page: Page;
  readonly inputName: Locator;
  readonly inputOrganizer: Locator;
  readonly inputVenue: Locator;
  readonly btnSave: Locator;
  readonly btnCancel: Locator;

  constructor(page: Page) {
    this.page = page;
    this.inputName = page.locator('input[id*="InputName"]');
    this.inputOrganizer = page.locator('input[id*="InputOrganizer"]');
    this.inputVenue = page.locator('input[id*="InputVenue"]');
    this.btnSave = page.locator('button[id*="BtnSave"]');
    this.btnCancel = page.locator('button[id*="BtnCancel"]');
  }

  async goto() {
    await this.page.goto('/');
    await this.page.waitForLoadState('domcontentloaded');
    await this.page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('TourneyAdmin', '');
      }
    });
  }
}
