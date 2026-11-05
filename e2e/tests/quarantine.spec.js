import { expect, test } from '@playwright/test'

// Quarantine round trip on a sandbox project. Needs a dedicated test account, given as repository secrets:
// E2E_EMAIL, E2E_PASSWORD and E2E_SLUG (a project of that account with at least one uploaded run).
// Without them the test is skipped, so the suite still runs read-only.
const { E2E_EMAIL, E2E_PASSWORD, E2E_SLUG } = process.env

test.describe('quarantine round trip', () => {
  test.skip(!E2E_EMAIL || !E2E_PASSWORD || !E2E_SLUG, 'set E2E_EMAIL, E2E_PASSWORD and E2E_SLUG to run')

  test('quarantine a test, see the badge, lift it again', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('Email').fill(E2E_EMAIL)
    await page.getByLabel('Password', { exact: true }).fill(E2E_PASSWORD)
    await page.getByRole('button', { name: 'Log in' }).click()
    await expect(page).toHaveURL(/\/projects$/)

    await page.goto(`/p/${E2E_SLUG}/tests?quarantined=false`)
    await page.locator('table tbody tr a').first().click()
    await expect(page.getByRole('heading', { name: 'Why this score?' })).toBeVisible()

    await page.getByRole('button', { name: 'Quarantine this test' }).click()
    await page.getByLabel('Reason').fill(`e2e check ${new Date().toISOString()}`)
    await page.getByRole('button', { name: 'Quarantine', exact: true }).click()
    await expect(page.getByText('🧊 Quarantined')).toBeVisible()

    await page.getByRole('button', { name: 'Lift quarantine' }).click()
    await expect(page.getByRole('button', { name: 'Quarantine this test' })).toBeVisible()
  })
})
