import { defineConfig, devices } from '@playwright/test'

// End-to-end tests against the deployed site (BASE_URL), run by .github/workflows/e2e.yml after each deploy.
export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: { timeout: 15_000 }, // the free backend may need a moment to wake up
  retries: 1, // FlakyHunter will happily tell us if these tests are flaky themselves
  reporter: [['list'], ['json', { outputFile: 'results.json' }], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.BASE_URL ?? 'https://flakyhunter.vercel.app',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
