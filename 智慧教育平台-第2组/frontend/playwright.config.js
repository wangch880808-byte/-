import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './tests/e2e', fullyParallel: false, workers: 1,
  use: { baseURL: 'http://127.0.0.1:5173', headless: true, viewport: { width: 1440, height: 960 }, screenshot: 'only-on-failure',
    ...(process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {}) },
  webServer: [
    { command: 'npm run dev', url: 'http://127.0.0.1:5173/app/', reuseExistingServer: !process.env.CI },
    { command: 'npm run preview', url: 'http://127.0.0.1:4173/app/index.html', reuseExistingServer: !process.env.CI }
  ],
  reporter: 'list'
})
