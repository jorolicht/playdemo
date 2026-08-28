import { Page, Locator, expect } from '@playwright/test';

/**
 * Page Object representing UserLogin (Login, Logout, Rate-Limiting & Dynamic Turnstile).
 */
export class LoginPage {
  readonly page: Page;
  readonly emailInput: Locator;
  readonly passwordInput: Locator;
  readonly loginBtn: Locator;
  readonly logoutBtn: Locator;
  readonly passkeyBtn: Locator;
  readonly errorAlert: Locator;
  readonly successAlert: Locator;
  readonly captchaContainer: Locator;

  constructor(page: Page) {
    this.page = page;
    this.emailInput = page.locator('input[id*="LoginId"]');
    this.passwordInput = page.locator('input[id*="PasswordId"]');
    this.loginBtn = page.locator('button[id*="BtnLogin"]');
    this.logoutBtn = page.locator('button[id*="BtnLogout"]');
    this.passkeyBtn = page.locator('button[id*="BtnPasskey"]');
    this.errorAlert = page.locator('#LoginErrorAlert');
    this.successAlert = page.locator('#LoginSuccessAlert');
    this.captchaContainer = page.locator('#CaptchaContainer');
  }

  async goto() {
    await this.page.goto('/');
    await this.page.waitForLoadState('domcontentloaded');
    await this.page.evaluate(() => {
      if (typeof (window as any).appLoadPage === 'function') {
        (window as any).appLoadPage('UserLogin', '');
      }
    });
    await expect(this.loginBtn.or(this.logoutBtn)).toBeVisible();
  }

  async login(email: string, pass: string) {
    await this.emailInput.fill(email);
    await this.passwordInput.fill(pass);
    await this.loginBtn.click();
    await this.page.waitForTimeout(600);
  }

  async logout() {
    if (await this.logoutBtn.isVisible()) {
      await this.logoutBtn.click();
      await this.page.waitForTimeout(600);
    }
  }
}
