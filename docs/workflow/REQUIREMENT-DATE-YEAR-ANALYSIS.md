# Requirement document date — wrong year (2025 vs 2026) analysis

**Issue**: Since around 2025-02-27, requirement documents have often had the **year** incorrectly shown as **2025** instead of **2026** (in document body dates, and in some filenames). The correct current year is **2026**.

**Analysis date**: 2026-03-04 (document updated after fix)

---

## 1. Observed pattern

| Type | Example | Correct? (current year = 2026) |
|------|--------|-----------------------------------|
| File name | `20260206-*.md`, `20260220-*.md`, …, `20260226-*.md` | ✅ 2026 |
| File name | `20250227-*.md`, `20250303-*.md`, `20250304-*.md` | ❌ 2025 (wrong year) |
| Body (§5, 작성일, Completed, Date) | `- 2025-02-27`, `**Date**: 2025-03-03` | ❌ 2025 (wrong year) |

So **filenames** and **in-document dates** showing **2025** when they should be **2026** are the problem.

---

## 2. Root cause

- The **conversation context** (e.g. Cursor's **user_info**: "Today's date: Wednesday Mar 4, **2025**") was supplying the **wrong year (2025)**. The agent used that when filling requirement doc filenames and in-document dates, so 2025 appeared instead of 2026.

---

## 3. Fix applied

1. **Single source of truth for current year**
   - **`.cursor/CURRENT-DATE-CONVENTION.md`** was added. It defines the **current year (2026)** and instructs to use it for requirement doc filenames and in-document dates, overriding any wrong year from the conversation context.

2. **Requirement-doc skill**
   - **`.cursor/skills/requirement-doc/SKILL.md`** was updated with a **§ Date source** section: when generating `yyyyMMdd` or any in-document date, **read and use** the current year (and month/day rules) from `.cursor/CURRENT-DATE-CONVENTION.md`.

3. **Template**
   - **`docs/template/REQUIREMENT_TEMPLATE.md`** was updated: for `yyyyMMdd` and in-document dates, use the current year and date from `.cursor/CURRENT-DATE-CONVENTION.md`.

**Maintenance**: Update the year in `.cursor/CURRENT-DATE-CONVENTION.md` when the calendar year changes (e.g. each January).

---

## 4. Summary

| Cause | Description |
|-------|-------------|
| **Wrong context date** | user_info (or similar) supplied "Today's date" with year **2025**; the agent used it. |
| **No override** | There was no project-level source for the correct year, so the wrong year persisted. |

**Fix**: Use `.cursor/CURRENT-DATE-CONVENTION.md` as the single source for the current year; requirement-doc skill and template reference it so the correct year (2026) is used regardless of context.
