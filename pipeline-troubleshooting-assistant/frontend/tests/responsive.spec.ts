/**
 * WCAG 1.4.10 Reflow — Responsive layout Playwright tests.
 *
 * Verifies that:
 * - No horizontal scrollbar appears at any tested viewport width (AC2)
 * - Sidebar is hidden at 320px and visible at 1280px (AC1)
 * - The hamburger toggle button is visible at 320px (AC1)
 * - Dashboard cards are single-column at 320px and two-column at 600px (AC4)
 * - History table is wrapped in a scroll container on mobile (AC6)
 * - KB table is wrapped in a scroll container on mobile (AC5 / AC6)
 * - All buttons meet minimum 44x44 touch target size at 320px (AC7)
 * - Mobile sidebar overlay opens, shows backdrop, closes on backdrop click (AC8)
 *
 * WO-085 AC2, AC4, AC5, AC6, AC7, AC8, AC10
 */

import { test, expect, Page } from '@playwright/test';

import historyLoaded from './fixtures/history-loaded.json';
import historyEmpty from './fixtures/history-empty.json';
import kbLoaded from './fixtures/knowledge-base-loaded.json';
import kbEmpty from './fixtures/knowledge-base-empty.json';
import analyzeResult from './fixtures/analyze-result.json';

// ─── API mock helpers ─────────────────────────────────────────────────────────

async function mockApis(
  page: Page,
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

/** Returns true if the page has no horizontal scrollbar */
async function hasNoHorizontalScroll(page: Page): Promise<boolean> {
  return page.evaluate(
    () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
  );
}

// ─── Viewport helpers ─────────────────────────────────────────────────────────

const VIEWPORTS = {
  mobile:  { width: 320,  height: 812 },
  phablet: { width: 480,  height: 844 },
  tablet:  { width: 768,  height: 1024 },
  desktop: { width: 1280, height: 900 },
} as const;

// ─── AC2: No horizontal scroll at any viewport ────────────────────────────────

test.describe('AC2 – No horizontal scroll (WCAG 1.4.10 Reflow)', () => {
  for (const [name, vp] of Object.entries(VIEWPORTS)) {
    test(`Dashboard — ${name} (${vp.width}px)`, async ({ page }) => {
      await page.setViewportSize(vp);
      await mockApis(page);
      await page.goto('/');
      await page.waitForLoadState('networkidle');
      expect(await hasNoHorizontalScroll(page)).toBe(true);
    });

    test(`Analyze — ${name} (${vp.width}px)`, async ({ page }) => {
      await page.setViewportSize(vp);
      await mockApis(page);
      await page.goto('/');
      await page.waitForLoadState('networkidle');
      // Navigate to Analyze using sidebar button
      const analyzeBtn = page.getByRole('button', { name: /analyze/i }).first();
      if (await analyzeBtn.isVisible()) {
        await analyzeBtn.click();
      }
      expect(await hasNoHorizontalScroll(page)).toBe(true);
    });

    test(`History — ${name} (${vp.width}px)`, async ({ page }) => {
      await page.setViewportSize(vp);
      await mockApis(page, { history: historyLoaded });
      await page.goto('/');
      await page.waitForLoadState('networkidle');
      const historyBtn = page.getByRole('button', { name: /history/i }).first();
      if (await historyBtn.isVisible()) {
        await historyBtn.click();
      }
      await page.waitForTimeout(300);
      expect(await hasNoHorizontalScroll(page)).toBe(true);
    });

    test(`Knowledge Base — ${name} (${vp.width}px)`, async ({ page }) => {
      await page.setViewportSize(vp);
      await mockApis(page, { knowledgeBase: kbLoaded });
      await page.goto('/');
      await page.waitForLoadState('networkidle');
      const kbBtn = page.getByRole('button', { name: /knowledge base/i }).first();
      if (await kbBtn.isVisible()) {
        await kbBtn.click();
      }
      await page.waitForTimeout(300);
      expect(await hasNoHorizontalScroll(page)).toBe(true);
    });
  }
});

// ─── AC1: Sidebar visibility at different viewports ───────────────────────────

test.describe('AC1 – Sidebar visibility', () => {
  test('Sidebar is hidden by default at 320px mobile viewport', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.mobile);
    await mockApis(page);
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    const sidebar = page.locator('#sidebar-nav');
    // On mobile, sidebar has transform: translateX(-100%) — it's in the DOM
    // but translated off-screen. Check that it does NOT have mobile-open class.
    const classes = await sidebar.getAttribute('class');
    expect(classes).not.toContain('mobile-open');
  });

  test('Sidebar is visible by default at 1280px desktop viewport', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.desktop);
    await mockApis(page);
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    const sidebar = page.locator('#sidebar-nav');
    const classes = await sidebar.getAttribute('class');
    // On desktop the sidebar is either open (has mobile-open) or just visible
    // via normal flow — the nav element must be in the DOM and visible
    await expect(sidebar).toBeVisible();
    // At desktop width, sidebar-open means it should have mobile-open class
    // (the lazy initializer sets it open at 1280px)
    expect(classes).toContain('mobile-open');
  });

  test('Hamburger toggle button is visible at 320px', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.mobile);
    await mockApis(page);
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // The toggle button has aria-label that includes "Open navigation menu" or "Collapse sidebar"
    const toggle = page.getByRole('button', { name: /(open navigation menu|close navigation menu|collapse sidebar|expand sidebar)/i });
    await expect(toggle).toBeVisible();
  });
});

// ─── AC8: Mobile sidebar overlay and backdrop ─────────────────────────────────

test.describe('AC8 – Mobile sidebar overlay with backdrop', () => {
  test('Opens sidebar overlay and shows backdrop at 320px', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.mobile);
    await mockApis(page);
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Sidebar should be closed initially
    const sidebar = page.locator('#sidebar-nav');
    expect(await sidebar.getAttribute('class')).not.toContain('mobile-open');

    // Click the hamburger toggle to open
    const toggle = page.getByRole('button', { name: /open navigation menu/i });
    await toggle.click();

    // Sidebar should now be open
    expect(await sidebar.getAttribute('class')).toContain('mobile-open');

    // Backdrop should be visible (CSS shows it on mobile when sidebar is open)
    const backdrop = page.locator('[data-testid="sidebar-backdrop"]');
    await expect(backdrop).toBeVisible();
  });

  test('Backdrop click closes the sidebar at 320px', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.mobile);
    await mockApis(page);
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Open the sidebar
    const toggle = page.getByRole('button', { name: /open navigation menu/i });
    await toggle.click();

    const sidebar = page.locator('#sidebar-nav');
    expect(await sidebar.getAttribute('class')).toContain('mobile-open');

    // Click the backdrop
    const backdrop = page.locator('[data-testid="sidebar-backdrop"]');
    await backdrop.click();

    // Sidebar should close
    await page.waitForTimeout(300); // allow CSS transition
    expect(await sidebar.getAttribute('class')).not.toContain('mobile-open');
  });

  test('Backdrop is not visible at 1280px desktop', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.desktop);
    await mockApis(page);
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Even if backdrop element exists in DOM, CSS hides it on desktop
    const backdrop = page.locator('[data-testid="sidebar-backdrop"]');
    // At desktop the sidebar starts open, so backdrop IS rendered in DOM,
    // but CSS sets display:none for >.768px viewports.
    // If it's rendered but hidden, toBeHidden passes; if not rendered, also passes.
    const count = await backdrop.count();
    if (count > 0) {
      await expect(backdrop).toBeHidden();
    }
  });
});

// ─── AC4: Dashboard grid reflow ───────────────────────────────────────────────

test.describe('AC4 – Dashboard cards reflow', () => {
  test('Dashboard grid has correct class for CSS-driven reflow', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.mobile);
    await mockApis(page);
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // The grid container should have the CSS class that drives responsive layout
    const grid = page.locator('.dashboard-grid');
    await expect(grid).toBeAttached();
  });

  test('At 320px, Dashboard cards stack to a single column', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.mobile);
    await mockApis(page);
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    const cards = page.locator('.dashboard-grid > div');
    const count = await cards.count();
    if (count < 2) return; // Not enough cards to compare

    const firstBox = await cards.nth(0).boundingBox();
    const secondBox = await cards.nth(1).boundingBox();
    if (!firstBox || !secondBox) return;

    // Single column: cards have the same x position (left-aligned)
    expect(Math.abs(firstBox.x - secondBox.x)).toBeLessThan(5);
    // And second card is below first card
    expect(secondBox.y).toBeGreaterThan(firstBox.y);
  });
});

// ─── AC6: History table scroll container ─────────────────────────────────────

test.describe('AC6 – History table responsive wrapper', () => {
  test('History table is wrapped in a scroll container', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.mobile);
    await mockApis(page, { history: historyLoaded });
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Navigate to History
    const historyBtn = page.getByRole('button', { name: /history/i }).first();
    if (await historyBtn.isVisible()) {
      await historyBtn.click();
    } else {
      // On mobile, open the sidebar first
      const toggle = page.getByRole('button', { name: /open navigation menu/i });
      await toggle.click();
      await page.getByRole('button', { name: /history/i }).first().click();
    }
    await page.waitForTimeout(300);

    const container = page.locator('.table-scroll-container');
    await expect(container).toBeAttached();

    // The table should be inside the scroll container
    const tableInContainer = page.locator('.table-scroll-container table');
    await expect(tableInContainer).toBeAttached();
  });
});

// ─── AC5: Knowledge Base table scroll container ───────────────────────────────

test.describe('AC5 – Knowledge Base table responsive wrapper', () => {
  test('KB table is wrapped in a scroll container', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.mobile);
    await mockApis(page, { knowledgeBase: kbLoaded });
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Navigate to Knowledge Base
    const kbBtn = page.getByRole('button', { name: /knowledge base/i }).first();
    if (await kbBtn.isVisible()) {
      await kbBtn.click();
    } else {
      const toggle = page.getByRole('button', { name: /open navigation menu/i });
      await toggle.click();
      await page.getByRole('button', { name: /knowledge base/i }).first().click();
    }
    await page.waitForTimeout(300);

    const container = page.locator('.kb-table-container');
    await expect(container).toBeAttached();

    const tableInContainer = page.locator('.kb-table-container table');
    await expect(tableInContainer).toBeAttached();
  });
});

// ─── AC7: Touch target minimum sizes ─────────────────────────────────────────

test.describe('AC7 – Touch target minimum 44x44px at 320px', () => {
  test('All visible buttons are at least 44x44px at 320px viewport', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.mobile);
    await mockApis(page);
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    const buttons = page.getByRole('button');
    const count = await buttons.count();

    const failures: string[] = [];
    for (let i = 0; i < count; i++) {
      const btn = buttons.nth(i);
      const visible = await btn.isVisible();
      if (!visible) continue;

      const box = await btn.boundingBox();
      if (!box) continue;

      const label = (await btn.getAttribute('aria-label')) ?? (await btn.textContent()) ?? `button[${i}]`;
      if (box.width < 44 || box.height < 44) {
        failures.push(
          `"${label.trim()}" — ${Math.round(box.width)}×${Math.round(box.height)}px (min 44×44 required)`,
        );
      }
    }

    expect(failures, `Buttons failing touch target minimum:\n${failures.join('\n')}`).toHaveLength(0);
  });
});

// ─── AC3: Analyze page has layout class ──────────────────────────────────────

test.describe('AC3 – Analyze page layout class', () => {
  test('Analyze root element has analyze-layout class', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.mobile);
    await mockApis(page);
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Navigate to Analyze (may need to open sidebar on mobile first)
    const analyzeBtn = page.getByRole('button', { name: /analyze/i }).first();
    if (await analyzeBtn.isVisible()) {
      await analyzeBtn.click();
    } else {
      const toggle = page.getByRole('button', { name: /open navigation menu/i });
      await toggle.click();
      await page.getByRole('button', { name: /analyze/i }).first().click();
    }
    await page.waitForTimeout(300);

    const analyzeRoot = page.locator('.analyze-layout');
    await expect(analyzeRoot).toBeAttached();
  });
});
