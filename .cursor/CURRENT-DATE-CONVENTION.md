# Current date convention (requirement docs and dates)

**Purpose**: Single source of truth for the **current year** (and date) used in requirement document filenames and in-document dates. This overrides any incorrect date from the conversation context (e.g. wrong "Today's date" in user_info).

## Current project date

- **Current year**: **2026**
- **Month and day**: Use the actual current calendar date. If the conversation context provides "Today's date: &lt;weekday&gt; &lt;Month&gt; &lt;day&gt;, &lt;year&gt;" and the **year** in that context is wrong, still use **year 2026** (from this file) and use the **month and day** from that context, or from the user, when available.

## When to use

- **Requirement doc filename**: `docs/requirements/yyyyMMdd-name.md` → use **2026** for `yyyy` and the actual month/day (e.g. 20260304 for March 4).
- **In-document dates**: §5 test run date, 작성일, Completed, Date, verification date, etc. → use **2026-MM-DD** (same rule: year from this file, month/day from actual date or context).

## Maintenance

- **Update the current year** in this file when the calendar year changes (e.g. each January). Optionally add a short note with the last-updated date (e.g. "Last updated: 2026-01").
- If the user explicitly says "we're in 20XX", treat that as overriding this file for that session.

## Reference

- Requirement doc skill: `.cursor/skills/requirement-doc/SKILL.md` (§ Path and name, § Date source).
- Template: `docs/template/REQUIREMENT_TEMPLATE.md`.
