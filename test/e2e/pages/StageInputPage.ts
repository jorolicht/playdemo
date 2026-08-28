import { Page, Locator, expect } from '@playwright/test';

export class StageInputPage {
  readonly page: Page;
  readonly setInputs: Locator;
  readonly tableInputs: Locator;
  readonly btnSave: Locator;
  readonly standingsTable: Locator;

  constructor(page: Page) {
    this.page = page;
    this.setInputs = page.locator('input[class*="set-input"], input[placeholder*="11:9"]');
    this.tableInputs = page.locator('input[class*="table-input"]');
    this.btnSave = page.locator('button:has-text("Speichern"), button[id*="BtnSave"]');
    this.standingsTable = page.locator('table');
  }

  async goto(stageId: string = '') {
    await this.page.goto('/');
    await this.page.waitForLoadState('domcontentloaded');
    await this.page.evaluate((id) => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('StageInput', id);
      }
    }, stageId);
  }
}
