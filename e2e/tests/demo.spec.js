import { expect, test } from '@playwright/test'

// Read-only checks of the public demo: they create no data, so they can run against production.

test.describe('public demo', () => {
  test('home page links to the live demo', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: /FlakyHunter/ })).toBeVisible()
    await expect(page.getByText('API: UP')).toBeVisible({ timeout: 90_000 }) // cold start of the free backend
    await page.getByRole('link', { name: 'View live demo' }).click()
    await expect(page).toHaveURL(/\/p\/demo$/)
  })

  test('dashboard shows KPIs, charts and test health without an account', async ({ page }) => {
    await page.goto('/p/demo')
    await expect(page.getByRole('heading', { name: 'Acme Shop (demo)' })).toBeVisible()
    await expect(page.locator('.demo-banner')).toContainText('public demo')
    await expect(page.locator('.kpi').getByText('Pass rate', { exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Pass rate per day' })).toBeVisible()
    await expect(page.locator('.recharts-surface').first()).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Test health' })).toBeVisible()
    // owner-only things are hidden for visitors
    await expect(page.getByRole('link', { name: 'Settings' })).toHaveCount(0)
  })

  test('the period switch reloads the numbers', async ({ page }) => {
    await page.goto('/p/demo')
    await page.getByRole('button', { name: 'Last 7 days' }).click()
    await expect(page.getByText(/runs in 7 days/)).toBeVisible()
  })

  test('test explorer filters by state and opens a test with its heatmap', async ({ page }) => {
    await page.goto('/p/demo/tests?state=FLAKY')
    const firstTest = page.locator('table tbody tr a').first()
    await expect(firstTest).toBeVisible()
    await firstTest.click()
    await expect(page).toHaveURL(/\/p\/demo\/tests\/\d+$/)
    await expect(page.getByRole('heading', { name: 'Why this score?' })).toBeVisible()
    await expect(page.locator('.heatmap .hcell').first()).toBeVisible()
    expect(await page.locator('.heatmap .hcell').count()).toBeGreaterThan(10)
  })

  test('failure groups are listed', async ({ page }) => {
    await page.goto('/p/demo/signatures')
    await expect(page.getByRole('heading', { name: 'Failure groups' })).toBeVisible()
    await expect(page.getByText(/× failed/).first()).toBeVisible()
  })
})

test.describe('accounts', () => {
  test('a wrong password shows a friendly error', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('Email').fill(`nobody-${Date.now()}@example.com`)
    await page.getByLabel('Password', { exact: true }).fill('definitely-wrong')
    await page.getByRole('button', { name: 'Log in' }).click()
    await expect(page.getByText('Invalid email or password')).toBeVisible()
  })

  test('private projects send visitors to the login page', async ({ page }) => {
    await page.goto('/p/some-private-project')
    await expect(page).toHaveURL(/\/login$/)
  })
})
