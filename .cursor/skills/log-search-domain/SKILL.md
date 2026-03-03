---
name: log-search-domain
description: >
  Log search: logType (pb_feplog, java_fw_imglog), LogDbSearchRequest,
  AdvancedSearchRequest, imagelog, pb_send/pb_recv. Use when user asks about
  log search, logType, pb_feplog, imagelog, or DB log search API.
  로그 검색, logType, pb_feplog, imagelog 관련 질문 시 사용.
---

# Log Search Domain

**Skill usage visibility**: When you use this skill to answer, state at the start of your response: `[Skill used: log-search-domain]`

Use for **log search, log types, and DB log APIs** in this repo. Scope: pb_feplog, java_fw_imglog(imagelog), search/advanced-search, field metadata.

## Quick reference

- **Log types**: pb_feplog (pb_send, pb_recv), java_fw_imglog (imagelog). GET /api/log-types.
- **Search API**: POST /api/logs/db-refactored/search (LogDbSearchRequest), POST /api/logs/db-refactored/advanced-search (java_fw_imglog only).
- **Advanced search**: AST 기반, queryText, filters, sort. java_fw_imglog만 지원; pb_feplog 시 UNSUPPORTED_LOG_TYPE.
- **Field metadata**: GET /api/log-types/{typeId}/fields — java_fw_imglog만 지원.

## When to use

- Log search, logType, pb_feplog, imagelog
- LogDbSearchRequest, AdvancedSearchRequest
- Search suggest, field metadata
- UNSUPPORTED_LOG_TYPE, LOG_TYPE_NOT_FOUND

## Document references

| Question type | Document | Section |
|---------------|----------|---------|
| Log types, search API | Path: `docs/api-definition.md` | `## 4. 로그 타입`, `## 5. DB 로그` |
| Advanced search | Path: `docs/api-definition.md` | `## 5.7 고급 검색` |
| Search suggest | Path: `docs/api-definition.md` | `## 6. 검색 추천` |
| Full list (전체 처리 이력) | Path: `docs/requirements/TOPIC-INDEX.md` | §image-log, §log-type |

## Code references

| Concern | Location |
|---------|----------|
| Log type controller | **backend/src/main/java/com/logmng/controller/LogTypeController.java** |
| DB log search | **backend/src/main/java/com/logmng/controller/LogDbController.java** |
| Search suggest | backend/src/main/java/com/logmng/controller/SearchSuggestController.java |
| Frontend log search | frontend/src/components/LogTypeSelector/LogTypeSelector.js, LogGrid/LogGrid.js |
| Image log | frontend/src/components/ImageLogTable/ImageLogTable.js |

## Before answering

1. logType: pb_feplog vs java_fw_imglog. Advanced search, field metadata, search suggest — java_fw_imglog만 지원.
2. UNSUPPORTED_LOG_TYPE: 해당 API가 java_fw_imglog만 지원할 때 pb_feplog 등으로 호출 시 반환.
3. **Requirement traceability**: When explaining design, cite requirement doc (path + §section).

## References

- API: docs/api-definition.md §4, §5, §6
- Improvement design: docs/SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md
