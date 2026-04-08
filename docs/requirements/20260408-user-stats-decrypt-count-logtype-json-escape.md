# 20260408 - User statistics decrypt count: logType JSON double-encoding

## 1. User requirement

### Requirement description

User activity statistics (daily/monthly) must count **decrypt** actions per log type (e.g. `java_fw_imglog`, `pb_feplog`). After a user performs a decrypt, the **today** (and aggregated) decrypt counts must reflect that activity.

### User scenario

1. User decrypts a row on imagelog (or analogous FEP path).
2. User opens activity statistics for the same date range and log type (or “all” types).
3. **Problem**: Decrypt count showed **0** even though decrypt occurred.

### Expected outcome

- Persisted `user_activity_log.action_detail` matches what `ActivityStatisticsService` filters on (`action_detail::text LIKE '%"logType":"<id>"%'`).
- No regression for search paths that already store plain `logType` inside structured maps.
- Activity audit parameters still mask sensitive string patterns where applicable; no additional PII in logs.

---

## 2. Design

### 2.1 Security review

- **PII / logging**: Fix only changes how **scalar** method parameters are placed into `requestParams` before JSON serialization. Strings still go through existing `maskSensitiveData`; numbers/booleans/characters are not free-text PII by default. No new fields logged.

### Technical design

#### Problem analysis (root cause)

1. `ActivityLogAspect` with `@ActivityLog(includeParams=true)` serialized non-DTO arguments via `ObjectMapper.writeValueAsString` on each argument. For a `String` path variable such as `logType`, that yields a JSON **string literal** (including quote characters in the Java string value).
2. That value was stored under `requestParams.logType`. When the full `action_detail` map was serialized for `user_activity_log.action_detail`, Jackson escaped those inner quotes (e.g. `\"java_fw_imglog\"`).
3. `ActivityStatisticsService` filters with `LIKE '%"logType":"java_fw_imglog"%'`. The double-encoded form does **not** contain that substring, so **DECRYPT** rows were excluded from per-log-type buckets and totals stayed **0**.

#### Diagnostic phase

Root cause confirmed by **static code analysis** (parameter loop + statistics WHERE clause); no production diagnostic logging required for this fix.

#### Solution approach

**Backend:**

- In `ActivityLogAspect`, for `includeParams`, when recording `requestParams`:
  - **String**: store the raw string; apply `maskSensitiveData` only (no `writeValueAsString` on the scalar).
  - **Number**, **Boolean**, **Character**: store as native JSON scalars (no pre-stringify).
  - **Map / DTO / collections / other**: keep existing deep-sanitize + `writeValueAsString` + `maskSensitiveData` on the JSON text.
- Reuse the same rules in catch-path fallbacks so retries do not reintroduce double-encoding.
- Extend `ActivityLogAspectTest` to assert persisted-shape JSON contains `"logType":"java_fw_imglog"` without the double-escaped value pattern.

### Planned change file list

#### Backend

- `backend/src/main/java/com/logmng/aspect/ActivityLogAspect.java` — scalar handling for `requestParams` values; shared helper; aligned catch paths.
- `backend/src/test/java/com/logmng/aspect/ActivityLogAspectTest.java` — TC for statistics-compatible `action_detail` JSON.

#### Docs

- `docs/requirements/20260408-user-stats-decrypt-count-logtype-json-escape.md` — this document.
- `docs/requirements/TOPIC-INDEX.md` — index entry.

---

## 3. Test approach

### Test case list

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-01 | Backend | Normal | `DecryptController.decryptRow`-like args: `logType` string + body map + `HttpServletRequest` | `requestParams.logType` equals plain `java_fw_imglog`; Servlet param placeholder unchanged | Unit (`mvn test`) |
| TC-02 | Backend | Normal | Same flow; serialize `action_detail` as DB would | JSON text contains substring `"logType":"java_fw_imglog"`; does not contain double-escaped `"logType\":\"java_fw_imglog\""` style value | Unit (`mvn test`) |
| TC-03 | Backend | Regression | `GET_PARTS` throwing request + decrypt args | No exception; placeholder for request param | Unit (existing) |

### Test scenarios

#### Scenario 1: Statistics substring

1. Run aspect around decrypt with path `logType = java_fw_imglog`.
2. Serialize captured `action_detail` with `ObjectMapper`.
3. Assert `%"logType":"java_fw_imglog"%` pattern would match (substring present).

---

## 5. Test results

- **Command**: `cd backend && mvn test`
- **Result**: **PASS** (exit code 0), 2026-04-08
- **Scope**: Full backend test suite; includes extended `ActivityLogAspectTest` (TC-04) and existing decrypt aspect tests.

---

## Checklist

- [x] Requirement doc completed (initial)
- [x] §5 updated after `mvn test`
