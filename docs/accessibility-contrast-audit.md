# Accessibility Color Contrast Audit — WO-083

**Scope:** Pipeline Troubleshooting Assistant frontend dark-sidebar / light-content theme  
**Standard:** WCAG 2.1 AA — 4.5:1 normal text, 3:1 large text and UI components  
**Date:** 2026-08-12  
**Tool:** Manual WCAG luminance formula + `@axe-core/playwright` automated checks  

---

## Background colors in use

| Token | Hex | Context |
|---|---|---|
| `--sidebar-bg` | `#101a2d` | Left sidebar navigation |
| `--main-bg` | `#f8fafc` | Main content area |
| `--card-bg` | `#ffffff` | Cards, modals, table backgrounds |
| `--expanded-row-bg` | `#f1f5f9` | History table expanded row |

---

## Full color-pair audit

### Sidebar (bg: `#101a2d`, L=0.010)

| Foreground | Hex | L | Ratio | Threshold | Status |
|---|---|---|---|---|---|
| Primary nav text | `#f1f5f9` | 0.915 | **14.6:1** | 4.5:1 | ✅ PASS |
| Inactive nav | `#cbd5e1` | 0.665 | **11.8:1** | 4.5:1 | ✅ PASS |
| Active nav / focus ring | `#60a5fa` | 0.363 | **6.8:1** | 4.5:1 | ✅ PASS |
| Toggle icon | `#e2e8f0` | 0.802 | **14.1:1** | 4.5:1 | ✅ PASS |
| Skip link text | `#60a5fa` | 0.363 | **6.8:1** | 4.5:1 | ✅ PASS |

### Main content area (bg: `#f8fafc`, L=0.955)

#### Text colors

| Foreground | Hex | L | Ratio vs `#f8fafc` | Ratio vs `#fff` | Threshold | Status |
|---|---|---|---|---|---|---|
| Primary text (browser default) | `#111827` | 0.015 | **18.5:1** | **19.0:1** | 4.5:1 | ✅ PASS |
| Secondary text (was `#64748b`) | `#475569` | 0.089 | **6.8:1** | **7.6:1** | 4.5:1 | ✅ PASS (fixed) |
| Error text (was `color:red`, `#ef4444`) | `#b91c1c` | 0.112 | **6.2:1** | **6.5:1** | 4.5:1 | ✅ PASS (fixed) |
| Success / sanitized notice (was `#22c55e`) | `#15803d` | 0.160 | **4.8:1** | **5.0:1** | 4.5:1 | ✅ PASS (fixed) |
| Confidence label (was `#555`) | `#475569` | 0.089 | **6.8:1** | **7.6:1** | 4.5:1 | ✅ PASS (fixed) |
| Char count (was `#666`) | `#475569` | 0.089 | **6.8:1** | **7.6:1** | 4.5:1 | ✅ PASS (fixed) |
| Link text | `#2563eb` | 0.153 | **4.95:1** | **5.3:1** | 4.5:1 | ✅ PASS |
| KB label text | `#374151` | 0.052 | **9.6:1** | **10.3:1** | 4.5:1 | ✅ PASS (unchanged) |
| KB description / delete modal | `#475569` | 0.089 | **6.8:1** | **7.6:1** | 4.5:1 | ✅ PASS |
| **OLD** secondary text | `#64748b` | 0.201 | **4.0:1** | **4.2:1** | 4.5:1 | ❌ FAIL → fixed |
| **OLD** error text | `#ef4444` | 0.229 | **3.7:1** | **3.8:1** | 4.5:1 | ❌ FAIL → fixed |
| **OLD** `color: red` | `#ff0000` | 0.213 | **3.8:1** | **4.0:1** | 4.5:1 | ❌ FAIL → fixed |
| **OLD** sanitized notice | `#22c55e` | 0.411 | **2.2:1** | **2.3:1** | 4.5:1 | ❌ FAIL → fixed |

#### Placeholder text

| Element | Color | L | Ratio vs `#fff` | Threshold | Status |
|---|---|---|---|---|---|
| All inputs / textarea | `#6b7280` | 0.168 | **4.8:1** | 4.5:1 | ✅ PASS (added CSS) |

#### UI component boundaries (borders — needs 3:1)

| Element | Border | L | Ratio vs `#f8fafc` | Ratio vs `#fff` | Threshold | Status |
|---|---|---|---|---|---|---|
| Card borders | `#64748b` | 0.201 | **4.0:1** | **4.2:1** | 3:1 | ✅ PASS (fixed) |
| Input borders | `#64748b` | 0.201 | **4.0:1** | **4.2:1** | 3:1 | ✅ PASS (fixed) |
| Table header border | `#64748b` | 0.201 | **4.0:1** | **4.2:1** | 3:1 | ✅ PASS (fixed) |
| Table row borders | `#64748b` | 0.201 | **4.0:1** | **4.2:1** | 3:1 | ✅ PASS (fixed) |
| Cancel button border | `#64748b` | 0.201 | n/a | **4.2:1** vs white | 3:1 | ✅ PASS (fixed) |
| Delete row button border | `#b91c1c` | 0.112 | n/a | **6.5:1** vs white | 3:1 | ✅ PASS (fixed) |
| **OLD** card borders | `#e2e8f0` | 0.802 | **1.2:1** | **1.2:1** | 3:1 | ❌ FAIL → fixed |
| **OLD** input borders | `#cbd5e1` | 0.665 | **1.5:1** | **1.5:1** | 3:1 | ❌ FAIL → fixed |
| **OLD** table borders | `#ddd`/`#eee` | 0.72/0.87 | **1.3:1** | **1.3:1** | 3:1 | ❌ FAIL → fixed |

#### Interactive element text (button / badge text on colored backgrounds)

| Foreground | Background | Contrast | Threshold | Status |
|---|---|---|---|---|
| `#fff` on `#2563eb` (Add Entry / Save) | 4.95:1 | 4.5:1 | ✅ PASS |
| `#fff` on `#b91c1c` (Delete button) | 6.5:1 | 4.5:1 | ✅ PASS (fixed from `#ef4444`) |
| `#b91c1c` on `#fff` (Delete row text) | 6.5:1 | 4.5:1 | ✅ PASS (fixed from `#ef4444`) |
| **OLD** `#fff` on `#ef4444` (Delete bg) | 3.76:1 | 4.5:1 | ❌ FAIL → fixed |
| **OLD** `#fff` on `#93c5fd` (disabled Save) | 1.80:1 | n/a (disabled exempt) | ⚠️ EXEMPT but improved |
| **OLD** `#fff` on `#fca5a5` (disabled Delete) | 1.90:1 | n/a (disabled exempt) | ⚠️ EXEMPT but improved |

**Note on disabled states:** WCAG SC 1.4.3 explicitly exempts inactive UI components from the contrast requirement. However, AC5 requires distinguishability without relying solely on color. Disabled buttons now use `opacity: 0.5` + `cursor: not-allowed` — both non-color cues — instead of a lighter background color.

#### Redaction chip (SanitizedLogDisplay / sanitization.ts)

| Foreground | Background (effective) | Contrast | Threshold | Status |
|---|---|---|---|---|
| `#6b21a8` on `rgba(107,33,168,0.1)` over `~#e5e5e5` | ~L 0.660 | **5.9:1** | 4.5:1 | ✅ PASS (fixed) |
| **OLD** `#a855f7` on `rgba(168,85,247,0.15)` over `~#e5e5e5` | ~L 0.660 | **3.9:1** | 4.5:1 | ❌ FAIL → fixed |

---

## Non-color information conveyed by color + text/icon

| Element | Color cue | Non-color cue | AC6 status |
|---|---|---|---|
| Severity (Critical/High/Medium/Low) | Text label only (no color-only badges) | Text label | ✅ PASS |
| Sanitization shield notice | Green `#15803d` | Shield icon + "Log sanitized before storage" text | ✅ PASS |
| Warning banner (potential secrets) | Red `#b91c1c` | AlertTriangle icon + descriptive warning text | ✅ PASS |
| Redaction chips `[X_REDACTED]` | Purple `#6b21a8` | Descriptive placeholder text e.g. `[AWS_KEY_REDACTED]` | ✅ PASS |
| Error messages | Red `#b91c1c` | "Error:" prefix text + `role="alert"` | ✅ PASS |
| Disabled buttons | Opacity 0.5 | `cursor: not-allowed` + opacity reduction | ✅ PASS |

---

## Automated test coverage

`@axe-core/playwright` contrast-audit Playwright tests cover:

- Dashboard page (empty and loaded states)
- Analyze page (empty form + with analysis result)
- History page (empty + expanded row with SanitizedLogDisplay)
- Knowledge Base page (empty + loaded + Add Entry modal + Delete modal)

Tests assert `violations.length === 0` for the `color-contrast` axe rule.

---

## Contrast ratio formula reference

Relative luminance: `L = 0.2126·R + 0.7152·G + 0.0722·B`  
(each channel linearised: `c ≤ 0.04045 → c/12.92`, else `((c+0.055)/1.055)^2.4`)  
Contrast ratio: `(L_lighter + 0.05) / (L_darker + 0.05)`
