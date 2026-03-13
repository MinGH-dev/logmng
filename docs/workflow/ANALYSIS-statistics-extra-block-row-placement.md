# Analysis: Statistics “기타 조건” row placement (row2 vs single row)

## Issue

User reported: field sizes are correctly applied, but **기타 조건** is placed in **row 3** on the statistics screen. They asked to check whether the tools or documents were not referenced properly, or whether the layout was undefined.

## Root cause: design doc conflict

### Two conflicting definitions

| Document | Rule | Resulting layout (statistics) |
|----------|------|-------------------------------|
| **docs/design/forms-and-filters.md** § Single row for non-date | “날짜·기간 블록을 제외한 나머지 블록(**로그 타입, 사용자, 기타 조건**)과 검색/초기화 버튼은 **단일 행**에 배치한다.” | One row: 로그 타입 + 사용자 + 기타 조건 + 검색/초기화 |
| **docs/design/search-fields-by-screen.md** §3 (통계) | “row1 = 로그 타입 + 사용자 블록, **row2 = 기타 조건**(IP 주소) + 검색/초기화 버튼” | Two rows: row1 = 로그 타입 + 사용자, row2 = 기타 조건 + buttons |

- **forms-and-filters.md** is the **generic layout rule**: “single row for non-date” for all user-context screens (activity log, statistics, etc.).
- **search-fields-by-screen.md** §3 describes statistics with **two** content rows (row1 / row2), so 기타 조건 is in the **second** content row. With the date/period in the header, the visual result is: row1 = date, row2 = 로그 타입 + 사용자, **row3 = 기타 조건 + buttons**.

Implementation (StatisticsFilters.js/css) followed **search-fields-by-screen.md** literally:  
Row 1 = 로그 타입 + UserContextFilterBlock, Row 2 = 기타 조건 + 검색/초기화. So 기타 조건 appears in the third visual row.

### Requirement doc (20260311)

The requirement doc says: “Activity log layout: Row1 = date fields only; **row2 = user block, 기타 조건, and filter actions (single row for non-date blocks)**. Per docs/design/forms-and-filters.md § Single row for non-date.” So the requirement **intended** a single non-date row (row2) containing user block + 기타 조건 + actions. For statistics, the same principle implies one non-date row: 로그 타입 + 사용자 + 기타 조건 + 검색/초기화.

## Conclusion

- **Cause**: **Design doc conflict**, not “undefined”.  
  - **forms-and-filters.md** clearly defines “단일 행” (single row) for non-date blocks.  
  - **search-fields-by-screen.md** §3 (통계) describes two rows (row1 / row2), which contradicts that rule and was implemented as-is.
- **Reference**: The implementation correctly followed **search-fields-by-screen.md** (per-screen table and “블록 순서”); the gap is that **search-fields-by-screen.md** was not aligned with **forms-and-filters.md** § Single row for non-date for the statistics screen.

## Correct layout (source of truth)

- **forms-and-filters.md** § Single row for non-date is the **canonical** rule.
- **Statistics**: One non-date row = 로그 타입 + 사용자 블록 + 기타 조건 + 검색/초기화 (all in the same row). Date/period stays in header (or row0).
- **Activity log**: Already correct — row1 = dates, row2 = 사용자 + 기타 조건 + 검색/초기화 (single row for non-date).

## Actions taken

1. **Design doc**: Updated **docs/design/search-fields-by-screen.md** §3 (통계): “블록 순서” and the field table now describe a **single** non-date row (로그 타입 + 사용자 블록 + 기타 조건 + 검색/초기화) and reference forms-and-filters.md § Single row for non-date. Table “위치” column for statistics fields now reads “단일 행(날짜 제외)” (or “단일 행(날짜 제외) 기타 조건” for ipAddress).
2. **Implementation**: Delegated to **Frontend** subagent — change **StatisticsFilters.js** and **StatisticsFilters.css** so that 기타 조건 is in the **same row** as 로그 타입 and 사용자 블록 (one row for all filter blocks + actions), not in a separate row below.

---

**Author**: Main agent (analysis)  
**Date**: 2026-03-13  
**Related**: req 20260311, docs/design/forms-and-filters.md § Single row for non-date, docs/design/search-fields-by-screen.md §3.
