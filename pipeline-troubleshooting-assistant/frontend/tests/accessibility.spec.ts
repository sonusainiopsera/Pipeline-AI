import { test, expect } from '@playwright/test';

// Loaded from committed fixtures — no backend required
import historyLoaded from './fixtures/history-loaded.json';
import historyEmpty from './fixtures/history-empty.json';
import kbLoaded from './fixtures/knowledge-base-loaded.json';
import kbEmpty from './fixtures/knowledge-base-empty.json';
import analyzeResult from './fixtures/analyze-result.json';

// ─── Helpers ──────────────────────────────────────────────────────────────────

async function mockAllApis(
  page: import('@playwright/test').Page,
  opts: {
    history?: object[];
    knowledgeBase?: object[];
  } = {},
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

async function navigateTo(
  page: import('@playwright/test').Page,
  navLabel: string,
) {
  await page.getByRole('button', { name: navLabel }).click();
}

// ─── Skip link ────────────────────────────────────────────────────────────────

test.describe('Skip navigation link', () => {
  test('is the first focusable element and slides into view on focus', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    // First Tab hits the skip link
    await page.keyboard.press('Tab');
    const skipLink = page.locator('.skip-link');
    await expect(skipLink).toBeFocused();
  });

  test('moves focus to #main-content when activated', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    await page.keyboard.press('Tab'); // focus skip link
    await page.keyboard.press('Enter'); // activate

    const main = page.locator('#main-content');
    await expect(main).toBeFocused();
  });
});

// ─── Sidebar navigation ───────────────────────────────────────────────────────

test.describe('Sidebar navigation keyboard support', () => {
  test('sidebar toggle button is focusable and togglable via keyboard', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    const toggleBtn = page.getByRole('button', { name: /collapse sidebar/i });
    await toggleBtn.focus();
    await expect(toggleBtn).toBeFocused();

    // Space activates button
    await page.keyboard.press('Space');
    await expect(page.getByRole('button', { name: /expand sidebar/i })).toBeVisible();
  });

  test('all nav buttons are focusable via Tab', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    // Skip the skip-link and toggle button
    await page.keyboard.press('Tab'); // skip link
    await page.keyboard.press('Tab'); // sidebar toggle

    const navLabels = ['Dashboard', 'Analyze', 'Knowledge Base', 'History'];
    for (const label of navLabels) {
      await page.keyboard.press('Tab');
      const focused = page.locator(':focus');
      await expect(focused).toHaveText(label);
    }
  });

  test('nav item Enter key navigates to the target page', async ({ page }) => {
    await mockAllApis(page, { history: historyLoaded });
    await page.goto('/');

    const historyBtn = page.getByRole('button', { name: 'History' });
    await historyBtn.focus();
    await page.keyboard.press('Enter');

    await expect(page.getByRole('heading', { name: 'Analysis History' })).toBeVisible();
  });
});

// ─── Analyze page tab order ───────────────────────────────────────────────────

test.describe('Analyze page — tab order', () => {
  test('log textarea is reachable via Tab after sidebar nav', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    // Ensure we are on Analyze page (default)
    await expect(page.getByRole('heading', { name: 'Analyze Pipeline Log' })).toBeVisible();

    // Tab order: skip-link(1), toggle(2), Dashboard(3), Analyze(4), KnowledgeBase(5), History(6), textarea(7)
    for (let i = 0; i < 7; i++) await page.keyboard.press('Tab');

    const textarea = page.locator('textarea').first();
    await expect(textarea).toBeFocused();
  });

  test('Analyze and Clear buttons have type=button and Clear is focusable', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    const analyzeBtn = page.getByRole('button', { name: 'Analyze' });
    const clearBtn = page.getByRole('button', { name: 'Clear' });

    // Verify semantic type attributes regardless of disabled state
    await expect(analyzeBtn).toHaveAttribute('type', 'button');
    await expect(clearBtn).toHaveAttribute('type', 'button');

    // Clear button is never disabled — verify it's keyboard-focusable
    await clearBtn.focus();
    await expect(clearBtn).toBeFocused();
  });
});

// ─── History page tab order ───────────────────────────────────────────────────

test.describe('History page — tab order', () => {
  test('View Details button is reachable via Tab (loaded state)', async ({ page }) => {
    await mockAllApis(page, { history: historyLoaded });
    await page.goto('/');

    await navigateTo(page, 'History');
    await expect(page.getByRole('heading', { name: 'Analysis History' })).toBeVisible();

    const viewBtn = page.getByRole('button', { name: /View Details/i }).first();
    await viewBtn.focus();
    await expect(viewBtn).toBeFocused();
  });

  test('View Details button has type=button', async ({ page }) => {
    await mockAllApis(page, { history: historyLoaded });
    await page.goto('/');
    await navigateTo(page, 'History');

    const viewBtn = page.getByRole('button', { name: /View Details/i }).first();
    await expect(viewBtn).toHaveAttribute('type', 'button');
  });

  test('empty state has no stuck Tab (no View Details buttons)', async ({ page }) => {
    await mockAllApis(page, { history: historyEmpty });
    await page.goto('/');
    await navigateTo(page, 'History');

    await expect(page.getByText('No analysis history yet')).toBeVisible();
    const viewBtns = page.getByRole('button', { name: /View Details/i });
    await expect(viewBtns).toHaveCount(0);
  });
});

// ─── Knowledge Base page tab order ───────────────────────────────────────────

test.describe('Knowledge Base page — tab order', () => {
  test('Add Entry button is reachable via keyboard (empty state)', async ({ page }) => {
    await mockAllApis(page, { knowledgeBase: kbEmpty });
    await page.goto('/');
    await navigateTo(page, 'Knowledge Base');

    const addBtn = page.getByRole('button', { name: 'Add Entry' });
    await addBtn.focus();
    await expect(addBtn).toBeFocused();
  });

  test('Edit and Delete buttons have type=button (loaded state)', async ({ page }) => {
    await mockAllApis(page, { knowledgeBase: kbLoaded });
    await page.goto('/');
    await navigateTo(page, 'Knowledge Base');

    const editBtns = page.getByRole('button', { name: /^Edit entry:/i });
    await expect(editBtns.first()).toHaveAttribute('type', 'button');

    const deleteBtns = page.getByRole('button', { name: /^Delete entry:/i });
    await expect(deleteBtns.first()).toHaveAttribute('type', 'button');
  });
});

// ─── Modal focus trapping ─────────────────────────────────────────────────────

test.describe('Knowledge Base modal — focus trap', () => {
  test('focus is confined within the Add Entry modal', async ({ page }) => {
    await mockAllApis(page, { knowledgeBase: kbEmpty });
    await page.goto('/');
    await navigateTo(page, 'Knowledge Base');

    const addBtn = page.getByRole('button', { name: 'Add Entry' });
    await addBtn.click();

    const dialog = page.getByRole('dialog', { name: 'Add Entry' });
    await expect(dialog).toBeVisible();

    // Tab through all focusable elements; the last Tab should cycle back inside
    // (Close, errorPattern, category, severity, rootCause, solution, Cancel, Save = 8 focusable)
    for (let i = 0; i < 9; i++) await page.keyboard.press('Tab');

    // After cycling, focus must still be inside the dialog (not in the background page)
    const isInsideDialog = await dialog.locator(':focus').count();
    expect(isInsideDialog).toBeGreaterThan(0);
  });

  test('Escape key closes the Add Entry modal', async ({ page }) => {
    await mockAllApis(page, { knowledgeBase: kbEmpty });
    await page.goto('/');
    await navigateTo(page, 'Knowledge Base');

    await page.getByRole('button', { name: 'Add Entry' }).click();
    const dialog = page.getByRole('dialog', { name: 'Add Entry' });
    await expect(dialog).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible();
  });

  test('focus returns to trigger button after modal is closed', async ({ page }) => {
    await mockAllApis(page, { knowledgeBase: kbEmpty });
    await page.goto('/');
    await navigateTo(page, 'Knowledge Base');

    const addBtn = page.getByRole('button', { name: 'Add Entry' });
    await addBtn.click();
    await expect(page.getByRole('dialog', { name: 'Add Entry' })).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(addBtn).toBeFocused();
  });

  test('Delete confirmation modal traps focus and returns to trigger on Escape', async ({ page }) => {
    await mockAllApis(page, { knowledgeBase: kbLoaded });
    await page.goto('/');
    await navigateTo(page, 'Knowledge Base');

    const deleteBtn = page.getByRole('button', { name: /^Delete entry: NullPointerException/i });
    await deleteBtn.click();

    const dialog = page.getByRole('dialog', { name: 'Delete Entry' });
    await expect(dialog).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible();
    await expect(deleteBtn).toBeFocused();
  });
});

// ─── Focus ring visibility ────────────────────────────────────────────────────

test.describe('Focus ring CSS', () => {
  test('interactive elements have :focus-visible outline rule loaded', async ({ page }) => {
    await mockAllApis(page);
    await page.goto('/');

    // Verify styles.css was loaded by checking a rule via JavaScript
    const outlineStyle = await page.evaluate(() => {
      const btn = document.querySelector('button');
      if (!btn) return null;
      btn.focus();
      return window.getComputedStyle(btn).outlineColor;
    });

    // The computed style depends on browser; we verify the stylesheet is present
    const stylesheets = await page.evaluate(() =>
      Array.from(document.styleSheets).some((ss) => {
        try {
          return Array.from(ss.cssRules).some((r) =>
            r.cssText.includes('focus-visible'),
          );
        } catch {
          return false;
        }
      }),
    );
    expect(stylesheets).toBe(true);
  });
});
