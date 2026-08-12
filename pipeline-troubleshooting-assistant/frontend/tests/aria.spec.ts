/**
 * ARIA labels and screen reader support — Playwright tests (WO-084).
 *
 * Verifies:
 * - axe-core ARIA-related rules pass on every page in multiple states
 * - aria-live result region is populated after analysis submission
 * - RouteAnnouncer updates its text content on page navigation
 * - Form inputs have associated labels
 * - Collapsed nav buttons retain accessible names
 */
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

import historyLoaded from './fixtures/history-loaded.json';
import historyEmpty from './fixtures/history-empty.json';
import kbLoaded from './fixtures/knowledge-base-loaded.json';
import kbEmpty from './fixtures/knowledge-base-empty.json';
import analyzeResult from './fixtures/analyze-result.json';

// ARIA-relevant axe rules to target — we run the full set via axe but check
// these explicitly; colour-contrast is covered separately in contrast-audit.spec.ts.
const ARIA_RULES = [
  'label',
  'aria-hidden-focus',
  'aria-valid-attr-value',
  'landmark-unique',
  'region',
];

// ─── Helpers ──────────────────────────────────────────────────────────────────

async function mockAllApis(
  page: import('@playwright/test').Page,
  opts: {
    history?: object[];
    knowledgeBase?: object[];
    analyzeResponse?: object;
  } = {},
) {
  const history = opts.history ?? historyEmpty;
  const kb = opts.knowledgeBase ?? kbEmpty;
  const analyze = opts.analyzeResponse ?? analyzeResult;

  await page.route('/api/history', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(history) }),
  );
  await page.route('/api/errors', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(kb) }),
  );
  await page.route('/api/analyze', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(analyze) }),
  );
}

async function navigateTo(page: import('@playwright/test').Page, navLabel: string) {
  await page.getByRole('button', { name: navLabel }).click();
}

// ─── axe-core ARIA rule checks — all pages ────────────────────────────────────

test.describe('axe-core ARIA rules — all pages', () => {
  test('Analyze page (default, empty) has no ARIA violations', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Analyze Pipeline Log' })).toBeVisible();

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(['color-contrast']) // covered by contrast-audit.spec.ts
      .analyze();

    expect(results.violations).toEqual([]);
  });

  test('Analyze page (with results) has no ARIA violations', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    await page.locator('#log-input').fill('outofmemoryerror heap space java.lang');
    await page.getByRole('button', { name: 'Analyze' }).click();
    await expect(page.getByText('BUILD_FAILURE')).toBeVisible();

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(['color-contrast'])
      .analyze();

    expect(results.violations).toEqual([]);
  });

  test('Dashboard page has no ARIA violations', async ({ page }) => {
    await mockAllApis(page, { history: historyLoaded });
    await page.goto('/');
    await navigateTo(page, 'Dashboard');
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(['color-contrast'])
      .analyze();

    expect(results.violations).toEqual([]);
  });

  test('History page (loaded) has no ARIA violations', async ({ page }) => {
    await mockAllApis(page, { history: historyLoaded });
    await page.goto('/');
    await navigateTo(page, 'History');
    await expect(page.getByRole('heading', { name: 'Analysis History' })).toBeVisible();

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(['color-contrast'])
      .analyze();

    expect(results.violations).toEqual([]);
  });

  test('History page (empty) has no ARIA violations', async ({ page }) => {
    await mockAllApis(page, { history: historyEmpty });
    await page.goto('/');
    await navigateTo(page, 'History');
    await expect(page.getByText('No analysis history yet')).toBeVisible();

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(['color-contrast'])
      .analyze();

    expect(results.violations).toEqual([]);
  });

  test('Knowledge Base page (loaded) has no ARIA violations', async ({ page }) => {
    await mockAllApis(page, { knowledgeBase: kbLoaded });
    await page.goto('/');
    await navigateTo(page, 'Knowledge Base');
    await expect(page.getByRole('heading', { name: 'Knowledge Base' })).toBeVisible();

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(['color-contrast'])
      .analyze();

    expect(results.violations).toEqual([]);
  });

  test('Knowledge Base Add Entry modal has no ARIA violations', async ({ page }) => {
    await mockAllApis(page, { knowledgeBase: kbEmpty });
    await page.goto('/');
    await navigateTo(page, 'Knowledge Base');
    await page.getByRole('button', { name: 'Add Entry' }).click();
    await expect(page.getByRole('dialog', { name: 'Add Entry' })).toBeVisible();

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(['color-contrast'])
      .analyze();

    expect(results.violations).toEqual([]);
  });
});

// ─── aria-live results region ─────────────────────────────────────────────────

test.describe('aria-live results region in Analyze page', () => {
  test('aria-live region is present in DOM before submission', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    // The aria-live container must exist before any content is added to it
    const liveRegion = page.locator('[aria-live="polite"]');
    await expect(liveRegion.first()).toBeAttached();
  });

  test('results category appears inside aria-live region after analysis', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    await page.locator('#log-input').fill('outofmemoryerror heap space java.lang');
    await page.getByRole('button', { name: 'Analyze' }).click();

    // Wait for result heading which is inside the aria-live region
    const resultHeading = page.locator('[aria-live="polite"] h3');
    await expect(resultHeading).toBeVisible({ timeout: 5000 });
    await expect(resultHeading).toContainText('BUILD_FAILURE');
  });
});

// ─── RouteAnnouncer ───────────────────────────────────────────────────────────

test.describe('RouteAnnouncer — page transition announcements', () => {
  test('route announcer element is present in the DOM', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    const announcer = page.locator('[data-testid="route-announcer"]');
    await expect(announcer).toBeAttached();
    await expect(announcer).toHaveAttribute('aria-live', 'assertive');
  });

  test('route announcer text updates after navigating to Dashboard', async ({ page }) => {
    await mockAllApis(page, { history: historyLoaded });
    await page.goto('/');

    await navigateTo(page, 'Dashboard');

    const announcer = page.locator('[data-testid="route-announcer"]');
    // Wait for the 100ms delay in RouteAnnouncer
    await expect(announcer).toHaveText('Dashboard', { timeout: 2000 });
  });

  test('route announcer text updates after navigating to History', async ({ page }) => {
    await mockAllApis(page, { history: historyLoaded });
    await page.goto('/');

    await navigateTo(page, 'History');

    const announcer = page.locator('[data-testid="route-announcer"]');
    await expect(announcer).toHaveText('Analysis History', { timeout: 2000 });
  });

  test('route announcer text updates after navigating to Knowledge Base', async ({ page }) => {
    await mockAllApis(page, { knowledgeBase: kbEmpty });
    await page.goto('/');

    await navigateTo(page, 'Knowledge Base');

    const announcer = page.locator('[data-testid="route-announcer"]');
    await expect(announcer).toHaveText('Knowledge Base', { timeout: 2000 });
  });

  test('document title updates on navigation', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    await navigateTo(page, 'Dashboard');
    await expect(page).toHaveTitle(/Dashboard — Pipeline AI/, { timeout: 2000 });

    await navigateTo(page, 'History');
    await expect(page).toHaveTitle(/Analysis History — Pipeline AI/, { timeout: 2000 });
  });
});

// ─── Form labels ──────────────────────────────────────────────────────────────

test.describe('Form inputs have associated labels', () => {
  test('log textarea is associated with a label element via htmlFor/id', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    const label = page.locator('label[for="log-input"]');
    await expect(label).toBeVisible();

    const textarea = page.locator('#log-input');
    await expect(textarea).toBeVisible();
  });

  test('customer update textarea has associated label when results shown', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    await page.locator('#log-input').fill('test log text');
    await page.getByRole('button', { name: 'Analyze' }).click();
    await expect(page.getByText('BUILD_FAILURE')).toBeVisible();

    const label = page.locator('label[for="customer-update"]');
    await expect(label).toBeVisible();
  });

  test('Knowledge Base form fields all have associated labels', async ({ page }) => {
    await mockAllApis(page, { knowledgeBase: kbEmpty });
    await page.goto('/');
    await navigateTo(page, 'Knowledge Base');
    await page.getByRole('button', { name: 'Add Entry' }).click();

    await expect(page.locator('label[for="kb-errorPattern"]')).toBeVisible();
    await expect(page.locator('label[for="kb-category"]')).toBeVisible();
    await expect(page.locator('label[for="kb-severity"]')).toBeVisible();
    await expect(page.locator('label[for="kb-rootCause"]')).toBeVisible();
    await expect(page.locator('label[for="kb-solution"]')).toBeVisible();
  });
});

// ─── Collapsed nav buttons retain accessible names ────────────────────────────

test.describe('Navigation buttons accessible names', () => {
  test('nav buttons have aria-label even when sidebar is collapsed', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    // Collapse sidebar
    await page.getByRole('button', { name: /collapse sidebar/i }).click();
    await expect(page.getByRole('button', { name: /expand sidebar/i })).toBeVisible();

    // Nav buttons should still have their accessible names
    for (const label of ['Dashboard', 'Analyze', 'Knowledge Base', 'History']) {
      const btn = page.getByRole('button', { name: label });
      await expect(btn).toBeAttached();
    }
  });
});

// ─── Landmarks ────────────────────────────────────────────────────────────────

test.describe('Semantic HTML landmarks', () => {
  test('page has a nav landmark with label', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    const nav = page.getByRole('navigation', { name: 'Primary navigation' });
    await expect(nav).toBeVisible();
  });

  test('page has a main landmark', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    const main = page.getByRole('main');
    await expect(main).toBeVisible();
  });
});
