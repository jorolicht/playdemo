import { defineConfig, devices } from '@playwright/test';
import dotenv from 'dotenv';
import path from 'path';

/**
 * Playwright-Konfiguration für die Screenshots des Benutzerhandbuchs (nicht Teil der Testsuite).
 * Aufruf: cd test/e2e && npx playwright test --config docs.config.ts
 * Die Bilder landen in site/content/hilfe/ und werden mit `sbt compile` ins Plugin kopiert.
 */
dotenv.config({ path: path.resolve(__dirname, '.env') });

export default defineConfig({
  testDir: './docs',
  fullyParallel: false,
  workers: 1,
  timeout: 300000,
  reporter: [['list']],
  use: {
    baseURL: process.env.WP_URL || 'https://localhost',
    ignoreHTTPSErrors: true,
    locale: 'de-DE',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 },
    // deutsche Datumsfelder (TT.MM.JJJJ) wie im Browser der Nutzer
    launchOptions: { args: ['--lang=de-DE'] } } }],
});
