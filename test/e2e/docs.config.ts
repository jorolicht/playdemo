import { defineConfig, devices } from '@playwright/test';
import dotenv from 'dotenv';
import path from 'path';

/**
 * Playwright-Konfiguration für die Screenshots des Benutzerhandbuchs (nicht Teil der Testsuite).
 * Aufruf: cd test/e2e && npx playwright test --config docs.config.ts          (Deutsch  -> site/content/hilfe/)
 *        cd test/e2e && HELP_LANG=en npx playwright test --config docs.config.ts (Englisch -> site/content/help/)
 * Die Bilder werden mit `sbt compile` ins Plugin kopiert.
 */
dotenv.config({ path: path.resolve(__dirname, '.env') });
const EN = process.env.HELP_LANG === 'en';

export default defineConfig({
  testDir: './docs',
  fullyParallel: false,
  workers: 1,
  timeout: 300000,
  reporter: [['list']],
  use: {
    baseURL: process.env.WP_URL || 'https://localhost',
    ignoreHTTPSErrors: true,
    locale: EN ? 'en-GB' : 'de-DE',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 },
    // Datumsfelder in der Sprache der Nutzer (DE: TT.MM.JJJJ, EN: DD/MM/YYYY)
    launchOptions: { args: [EN ? '--lang=en-GB' : '--lang=de-DE'] } } }],
});
