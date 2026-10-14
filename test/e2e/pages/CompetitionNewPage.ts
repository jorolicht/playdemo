import { Page, Locator, expect } from '@playwright/test';

/**
 * Page Object Model for CompetitionNew page (Schnellstart / Quick Start).
 */
export class CompetitionNewPage {
  readonly page: Page;

  readonly inputName: Locator;
  readonly selectTyp: Locator;
  readonly selectCategory: Locator;
  readonly inputStartDate: Locator;
  readonly inputTtrFrom: Locator;
  readonly inputTtrTo: Locator;
  readonly btnSave: Locator;
  readonly btnCancel: Locator;

  constructor(page: Page) {
    this.page = page;
    this.inputName = page.locator('input[id*="CompetitionNew_CompNameId"]');
    this.selectTyp = page.locator('select[id*="CompetitionNew_CompTypId"]');
    this.selectCategory = page.locator('select[id*="CompetitionNew_CompCategoryId"]');
    this.inputStartDate = page.locator('input[id*="CompetitionNew_StartDateId"]');
    this.inputTtrFrom = page.locator('input[id*="CompetitionNew_TtrFromId"]');
    this.inputTtrTo = page.locator('input[id*="CompetitionNew_TtrToId"]');
    this.btnSave = page.locator('button[id*="CompetitionNew_BtnSave"]');
    this.btnCancel = page.locator('button:has-text("Abbrechen"), button:has-text("Cancel")');
  }

  async fillForm(data: { name: string; typ?: string; category?: string; startDate?: string; ttrFrom?: number; ttrTo?: number }) {
    await this.inputName.fill(data.name);
    if (data.typ) await this.selectTyp.selectOption(data.typ);
    if (data.category) await this.selectCategory.selectOption(data.category);
    if (data.startDate) await this.inputStartDate.fill(data.startDate);
    if (data.ttrFrom !== undefined) await this.inputTtrFrom.fill(data.ttrFrom.toString());
    if (data.ttrTo !== undefined) await this.inputTtrTo.fill(data.ttrTo.toString());
  }

  async save() {
    await this.btnSave.click();
  }
}
