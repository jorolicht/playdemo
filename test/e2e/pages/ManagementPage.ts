import { Page, Locator, expect } from '@playwright/test';

export class ManagementPage {
  readonly page: Page;
  readonly userSearchInput: Locator;
  readonly userTable: Locator;
  readonly availableInput: Locator;
  readonly btnSaveUser: Locator;

  constructor(page: Page) {
    this.page = page;
    this.userSearchInput = page.locator('input[id*="UserSearch"]');
    this.userTable = page.locator('table');
    this.availableInput = page.locator('input[id*="Available"]');
    this.btnSaveUser = page.locator('button:has-text("Speichern"), button:has-text("Update")');
  }

  async goto() {
    await this.page.goto('/');
    await this.page.waitForLoadState('domcontentloaded');
    await this.page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('Management', '');
      }
    });
  }
}
