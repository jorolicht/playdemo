import { Page, Locator, expect } from '@playwright/test';

export class PlayerListPage {
  readonly page: Page;
  readonly btnAddSingle: Locator;
  readonly btnAddDouble: Locator;
  readonly btnImportFile: Locator;
  readonly fileInput: Locator;
  readonly playerTable: Locator;

  constructor(page: Page) {
    this.page = page;
    this.btnAddSingle = page.locator('button:has-text("Spieler hinzufügen"), button:has-text("Einzelspieler")');
    this.btnAddDouble = page.locator('button:has-text("Doppel"), button:has-text("Paarung")');
    this.btnImportFile = page.locator('button:has-text("Import"), button:has-text("Datei")');
    this.fileInput = page.locator('input[type="file"]');
    this.playerTable = page.locator('table');
  }

  async goto() {
    await this.page.goto('/');
    await this.page.waitForLoadState('domcontentloaded');
    await this.page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('PlayerList', '');
      }
    });
  }
}
