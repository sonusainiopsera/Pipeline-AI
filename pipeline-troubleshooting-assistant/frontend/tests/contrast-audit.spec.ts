/**
 * WCAG AA color-contrast automated audit using @axe-core/playwright.
 * Runs the axe-core 'color-contrast' rule against every page with realistic
 * mock data. Any violation causes the test to fail, catching regressions.
 *
 * AC7: automated axe-core check in the build pipeline
 * AC9: asserts zero color-contrast violations across all rendered pages
 */
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

import historyLoaded from './fixtures/history-loaded.json';
import historyEmpty from './fixtures/history-empty.json';
import kbLoaded from './fixtures/knowledge-base-loaded.json';
import kbEmpty from './fixtures/knowledge-base-empty.json';
import analyzeResult from './fixtures/analyze-result.json';

// ─── Helpers ──────────────────────────────────────────────────────────────────

async function mockAllApis(
  page: import('@playwright/test').Page,
  opts: { history?: object[]; knowledgeBase?: object[] } = {},
) {
  const history = opts.history ?? historyEmpty;
  const kb = opts.knowledgeBase ?? kbEmpty;

  await page.route('/api/history', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(history) }),
  );
  await page.route('/api/errors', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(kb) }),
  );
  await page.route('/api/analyze', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(analyzeResult) }),
  );
}

// ─── Contrast audit helpers ───────────────────────────────────────────────────

function formatViolations(violations: import('axe-core').Result[]): string {
  return violations
    .map(
      (v) =>
        `[${v.id}] ${v.description}\n  Nodes: ${v.nodes
          .map((n) => n.target.join(', '))
          .join(' | ')}`,
    )
    .join('\n\n');
}

// ─── Page contrast audits ─────────────────────────────────────────────────────

test.describe('WCAG AA color-contrast — Dashboard page', () => {
  test('no color-contrast violations (empty state)', async ({ page }) => {
    await mockAllApis(page, { history: historyEmpty });
    await page.goto('/');
    await page.getByRole('button', { name: 'Dashboard' }).click();
    await page.waitForSelector('h1, h2');

    const results = await new AxeBuilder({ page })
      .withRules(['color-contrast'])
      .analyze();

    expect(results.violations, formatViolations(results.violations)).toHaveLength(0);
  });

  test('no color-contrast violations (loaded state)', async ({ page }) => {
    await mockAllApis(page, { history: historyLoaded });
    await page.goto('/');
    await page.getByRole('button', { name: 'Dashboard' }).click();
    await page.waitForSelector('h1, h2');

    const results = await new AxeBuilder({ page })
      .withRules(['color-contrast'])
      .analyze();

    expect(results.violations, formatViolations(results.violations)).toHaveLength(0);
  });
});

test.describe('WCAG AA color-contrast — Analyze page', () => {
  test('no color-contrast violations (empty form)', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    const results = await new AxeBuilder({ page })
      .withRules(['color-contrast'])
      .analyze();

    expect(results.violations, formatViolations(results.violations)).toHaveLength(0);
  });

  test('no color-contrast violations (with analysis result)', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    // Fill and submit to get result panel visible
    await page.locator('textarea').first().fill('docker daemon failed connection refused');
    await page.getByRole('button', { name: 'Analyze' }).click();
    await page.waitForSelector('[data-testid="analysis-result"], h3', { timeout: 5000 }).catch(() => {});

    const results = await new AxeBuilder({ page })
      .withRules(['color-contrast'])
      .analyze();

    expect(results.violations, formatViolations(results.violations)).toHaveLength(0);
  });
});

test.describe('WCAG AA color-contrast — History page', () => {
  test('no color-contrast violations (empty state)', async ({ page }) => {
    await mockAllApis(page, { history: historyEmpty });
    await page.goto('/');
    await page.getByRole('button', { name: 'History' }).click();
    await page.waitForSelector('h2');

    const results = await new AxeBuilder({ page })
      .withRules(['color-contrast'])
      .analyze();

    expect(results.violations, formatViolations(results.violations)).toHaveLength(0);
  });

  test('no color-contrast violations (loaded state, row expanded)', async ({ page }) => {
    await mockAllApis(page, { history: historyLoaded });
    await page.goto('/');
    await page.getByRole('button', { name: 'History' }).click();
    await page.waitForSelector('table');

    // Expand the first row to exercise SanitizedLogDisplay contrast
    const viewBtn = page.getByRole('button', { name: /View Details/i }).first();
    if (await viewBtn.isVisible()) {
      await viewBtn.click();
      await page.waitForSelector('[id^="history-detail-"]');
    }

    const results = await new AxeBuilder({ page })
      .withRules(['color-contrast'])
      .analyze();

    expect(results.violations, formatViolations(results.violations)).toHaveLength(0);
  });
});

test.describe('WCAG AA color-contrast — Knowledge Base page', () => {
  test('no color-contrast violations (empty state)', async ({ page }) => {
    await mockAllApis(page, { knowledgeBase: kbEmpty });
    await page.goto('/');
    await page.getByRole('button', { name: 'Knowledge Base' }).click();
    await page.waitForSelector('h1');

    const results = await new AxeBuilder({ page })
      .withRules(['color-contrast'])
      .analyze();

    expect(results.violations, formatViolations(results.violations)).toHaveLength(0);
  });

  test('no color-contrast violations (loaded state)', async ({ page }) => {
    await mockAllApis(page, { knowledgeBase: kbLoaded });
    await page.goto('/');
    await page.getByRole('button', { name: 'Knowledge Base' }).click();
    await page.waitForSelector('table');

    const results = await new AxeBuilder({ page })
      .withRules(['color-contrast'])
      .analyze();

    expect(results.violations, formatViolations(results.violations)).toHaveLength(0);
  });

  test('no color-contrast violations — Add Entry modal open', async ({ page }) => {
    await mockAllApis(page, { knowledgeBase: kbEmpty });
    await page.goto('/');
    await page.getByRole('button', { name: 'Knowledge Base' }).click();
    await page.getByRole('button', { name: 'Add Entry' }).click();
    await page.waitForSelector('[role="dialog"]');

    const results = await new AxeBuilder({ page })
      .withRules(['color-contrast'])
      .analyze();

    expect(results.violations, formatViolations(results.violations)).toHaveLength(0);
  });

  test('no color-contrast violations — Delete confirmation modal open', async ({ page }) => {
    await mockAllApis(page, { knowledgeBase: kbLoaded });
    await page.goto('/');
    await page.getByRole('button', { name: 'Knowledge Base' }).click();
    await page.waitForSelector('table');

    const deleteBtn = page.getByRole('button', { name: /^Delete entry:/i }).first();
    if (await deleteBtn.isVisible()) {
      await deleteBtn.click();
      await page.waitForSelector('[role="dialog"]');
    }

    const results = await new AxeBuilder({ page })
      .withRules(['color-contrast'])
      .analyze();

    expect(results.violations, formatViolations(results.violations)).toHaveLength(0);
  });
});
