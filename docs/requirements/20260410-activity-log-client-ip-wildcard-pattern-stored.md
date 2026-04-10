# 20260410 - Activity log stores allowlist IP pattern instead of client IP

## 1. User requirement

### Requirement description

**Summary of user report (Korean → English):** The system administrator configures **allowed connection IPs** as patterns such as `172.23.111.*` (comma-separated allowlist, often aligned with `app.security.authorized-ips`). **User activity history** (`user_activity_log` / activity-log UI) shows that **same literal pattern string** (e.g. `172.23.111.*`) in the IP column instead of the **actual client IP address** observed for the request. The product needs **root-cause analysis** and **remediation** so activity history records and displays a **real client IP** (or a clearly defined fallback when the true client cannot be determined), not an **allowlist pattern** or other **non-IP** string.

### User scenario

1. Administrator sets global or deployment allowlist entries including **wildcard IPv4 patterns** (e.g. `172.23.111.*`).
2. A user performs authenticated actions that emit **@ActivityLog** events (e.g. login, searches, admin actions).
3. Operator opens **Activity log** and inspects the **IP** field for those rows.
4. **Problem:** The IP column shows **`172.23.111.*`** (or another allowlist pattern) instead of a concrete address such as **`172.23.111.42`**.

### Expected outcome

- **Persisted** `user_activity_log.ip_address` (and API-facing values derived from it) reflect the **resolved client IP** for the HTTP request where technically possible (subject to reverse-proxy and header trust policies).
- Values stored and shown are **valid IP literals** (IPv4/IPv6) or a **documented sentinel** (e.g. empty/null/`unknown`) — **not** allowlist **pattern strings** (`*`-suffix segments), unless product explicitly defines otherwise (default: **must not** store patterns).
- **Audit usefulness:** Operators can distinguish sessions by IP; incorrect literals do not undermine investigations.
- **No production noise:** Any diagnostic instrumentation used during root-cause analysis must follow §2 (DEBUG / dev-only / removed after verification).

---

## 2. Design

### 2.1 Security review

- **Operational / audit metadata:** Client IP in activity log supports security and operations. Storing **non-literal** strings confuses audits and may **obscure** real origin hints.
- **Header trust:** Relying on `X-Forwarded-For` / `X-Real-IP` must align with deployment trust boundaries; validation reduces injection of arbitrary strings (including pasted allowlist tokens) into persisted fields.
- **PII:** IP can be sensitive in some jurisdictions; this requirement **does not** expand collection — it **corrects** wrong values. Diagnostic logs must avoid **production** emission (see diagnostic phase).

### Technical design

#### Codebase summary (relevant areas)

- **`ActivityLogAspect`** (`backend/.../aspect/ActivityLogAspect.java`): After `@ActivityLog` methods, **`getClientIpAddress(HttpServletRequest)`** builds a list of **candidate** strings from **`X-Forwarded-For`**, **`X-Real-IP`**, **`getRemoteAddr()`**, and (when localhost) local interface IPv4 addresses. It **prefers** a candidate that passes **`isPrivateIp`**; otherwise it picks the **first non-127.0.0.1** candidate, else **`RemoteAddr`**. **There is no check** that a candidate is a **syntactically valid IP literal** before persisting.
- **`IpUtil`** (`backend/.../util/IpUtil.java`): **`getClientIP`** uses a **simpler** header chain and returns the first comma-separated **`X-Forwarded-For`** entry or **`RemoteAddr`**. **`isAuthorizedIP(clientIP, authorizedIPs)`** compares the **resolved** client string against **exact** entries and **`*.` wildcard prefixes** in **`app.security.authorized-ips`** — the **pattern** lives only in config, not in **`getClientIP`** output.
- **Login path:** **`AuthService.login`** uses **`ipUtil.getClientIP(httpRequest)`** for **`IP_ACCESS_DENIED`** checks and sets **`LoginResponse.clientIP`** from that value — **not** from allowlist patterns.
- **Other writers:** Some controllers pass **`request.getRemoteAddr()`** directly into services that call **`UserActivityLogService.saveActivityLog`**; aspect-based paths use **`ActivityLogAspect`** only.

#### Problem analysis (hypotheses — confirm in diagnostic phase)

1. **Unvalidated forwarded headers:** If **`X-Forwarded-For`** (or **`X-Real-IP`**) is mis-set to a **non-literal** value (e.g. the same string as an allowlist line **`172.23.111.*`** due to misconfiguration, or any non-numeric token), **`getClientIpAddress`** may still **select** that string as **`selectedIp`** because **`isPrivateIp`** fails for non-numeric octets and the **“first non-localhost candidate”** branch can accept **arbitrary** strings.
2. **Divergence from `IpUtil`:** Activity logging uses **different** resolution rules than auth/IP allowlist checks, increasing the risk of **inconsistent** stored values vs. **`LoginResponse.clientIP`** for the same session.
3. **Infrastructure vs. code:** Less likely: **`RemoteAddr`** returning a pattern — still, validation at the persistence boundary is the **defense in depth** requirement.

#### Diagnostic phase (mandatory — error / bug fix)

Per **error-first workflow**, the implementer **must not** ship a logic fix based on hypothesis alone.

- **Phase 0 — Diagnostic**
  1. Add **diagnostic** logs at **DEBUG** (or behind **dev-only** flag) in **`ActivityLogAspect.getClientIpAddress`** (and/or a single shared resolver if introduced): log **raw** relevant headers (`X-Forwarded-For`, `X-Real-IP`), **`getRemoteAddr()`**, **full candidate list**, and **chosen** value **before** persistence. **Do not** log secrets; IP headers are operational.
  2. **Reproduce** in a controlled environment (staging or local with the same proxy header behavior as production): confirm whether **`172.23.111.*`** appears in **candidates** or only after selection.
  3. **Analyze** logs to **confirm** which input path supplies the pattern string.
  4. Only after root cause is **confirmed**, implement the **remediation** below.
- **Production safety:** Diagnostic logs **must** be **DEBUG** (disabled in typical prod), or **feature-flag / dev-only**, or **removed** after verification. **No** sustained **INFO** spam for IP diagnostics in production.

#### Solution approach

**Backend:**

- **Unify or align** client IP resolution used for **activity persistence** with **`IpUtil.getClientIP`** (preferred: **one** well-tested method used by both **auth** and **activity log**, or **`IpUtil`** extended with optional “private-prefer” policy if product still requires **`ActivityLogAspect`**’s current preference rules).
- **Validate** any string before persisting to **`user_activity_log.ip_address`**: accept only **parseable IPv4/IPv6 literals** (e.g. **`InetAddress`** validation / strict regex). If no candidate is valid, fall back to **`getRemoteAddr()`** if valid, else **`null`** or **empty** per existing DB nullability and API contract — **never** persist **wildcard patterns** or **non-IP** tokens from headers.
- **Reduce `INFO` logging** in hot paths if current **`log.info`** for every IP selection is too noisy for production (optional cleanup in same change if confirmed by ops).
- **Tests:** Unit tests for **`getClientIpAddress`** / shared resolver: valid **`X-Forwarded-For`**, invalid token **`172.23.111.*`**, IPv6 localhost normalization, alignment with **`IpUtil`** for a shared scenario.

**Frontend:**

- **None** unless investigation shows the UI **displays a field other than** API `ip_address` / search result IP (unlikely). Re-verify activity-log table after backend fix.

**DB:**

- **No** schema migration expected; **data cleanup** of historical wrong rows is **out of scope** unless product requests a one-off script (optional follow-up).

### Affected scopes and change targets (verification)

Per **`docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`**.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | No | N/A |
| DB | No | N/A |
| Contract / Spec | Optional narrative | Yes — clarify **semantic** of stored IP as **literal** (if not already explicit); **no** breaking JSON shape |
| Cursor tools (skills, specs) | Optional | Yes — update **`.cursor/skills/activity-statistics-domain/SKILL.md`** or **`auth-permission-domain`** only if IP resolution behavior is documented there |

**Pattern 3.4 (search/filter UI)** does **not** apply.

### Planned change file list (expected change targets)

#### Backend — **actual (verified)**

- `backend/src/main/java/com/logmng/aspect/ActivityLogAspect.java` — **`resolveClientIpForActivityLog`**: DEBUG-only diagnostic line (raw `X-Forwarded-For`, `X-Real-IP`, `remoteAddr`, `getClientIP`, ordered candidates, resolved); persists via **`IpUtil.getResolvedClientIpForActivityLog`**; removed prior **INFO** hot-path IP selection logs and unvalidated candidate selection.
- `backend/src/main/java/com/logmng/util/IpUtil.java` — **`parseValidIpLiteralOrNull`**, **`normalizeServletRemoteAddr`**, **`getResolvedClientIpForActivityLog`**, **`collectIpCandidatesInTrustOrder`** (same header precedence as **`getClientIP`**, XFF split for per-hop validation).
- `backend/src/test/java/com/logmng/aspect/ActivityLogAspectTest.java` — TC-01–TC-03 (persisted IP via stub capture).
- `backend/src/test/java/com/logmng/util/IpUtilTest.java` — TC-04, pattern rejection, invalid-first-XFF hop.
- `backend/src/test/java/com/logmng/service/StubUserActivityLogServiceSaveCapture.java` — captures **`lastIpAddress`** for aspect tests.

#### Contract / docs (optional, same change batch or follow-up)

- `docs/api-definition.md` — **done**: **`ip_address`** semantic under activity-log list response (validated literal, not allowlist patterns).
- `docs/contract.md` — only if the same clarification is duplicated there per **DOC-CODE-SYNC** — **not changed** (api-definition only).

#### Cursor tools (optional)

- `.cursor/skills/activity-statistics-domain/SKILL.md` — short note that activity rows use **server-resolved** client IP literals when describing audit behavior.

---

## 3. Test approach

### Test case list

**Scope tags** on each TC for handoff (`HANDOFF-CHECKLIST.md`).

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-01 | Backend | Normal | Mock request: `X-Forwarded-For` = valid public or private IPv4, `RemoteAddr` = proxy | Persisted IP equals **trusted** resolved literal per product rules (first hop or **`IpUtil`**-aligned) | Unit (`mvn test`) |
| TC-02 | Backend | Edge | `X-Forwarded-For` = `172.23.111.*` (non-literal), `RemoteAddr` = valid IPv4 | Persisted IP is **`RemoteAddr`** (or other **valid** fallback), **not** the pattern string | Unit (`mvn test`) |
| TC-03 | Backend | Edge | No forwarded headers; `RemoteAddr` = `172.23.111.10` | Persisted IP is **`172.23.111.10`** | Unit (`mvn test`) |
| TC-04 | Backend | Regression | Compare **`IpUtil.getClientIP`** vs activity resolver for the same mock request where unified | **Same** string when unified behavior is required; if product keeps “private prefer” only in aspect, document **intentional** delta in §6 | Unit (`mvn test`) |
| TC-05 | Integration | Normal | Authenticated request through stack with real **`X-Forwarded-For`** (staging) | Activity-log API returns **literal** IP matching expected client; **not** allowlist pattern | Manual / integration (document curl in §5) |

### Test scenarios

#### Scenario 1: Mis-set forwarded header

1. Configure mock **`HttpServletRequest`** with **`X-Forwarded-For`** = `172.23.111.*` and valid **`RemoteAddr`**.
2. Trigger aspect path that persists activity (or call resolver directly in unit test).
3. Assert DB field / **`saveActivityLog`** argument **does not** equal `172.23.111.*`.

#### Scenario 2: Login vs activity IP consistency

1. Same request: record **`LoginResponse.clientIP`** (or **`IpUtil.getClientIP`**) and aspect-chosen IP after fix.
2. Assert **consistent** policy per §2 solution (unified or documented delta).

### Test data

- Use **string literals** `172.23.111.*` vs `172.23.111.10` in tests; no DB seed required for unit tests.

### Test environment

- Backend: `http://localhost:9200` (per contract).
- Integration: staging with reverse proxy mirroring production header behavior.

---

## 4. Checklist

### Frontend verification

- [ ] Activity-log list/detail still renders IP column; values show **literal** addresses after backend fix (smoke). **Note (QA)**: Backend-only change; optional UI smoke when an operator exercises Activity log against a running frontend — not run in this QA pass.

### Backend verification

- [x] Unit tests (TC-01–TC-04) pass.
- [x] Diagnostic logs **disabled** or **DEBUG-only** in production configuration (single **DEBUG** line in aspect; no sustained **INFO** IP selection logs).
- [x] No new **INFO** noise without ops agreement.

### Integration

- [ ] TC-05 or equivalent manual verification recorded in §5. **Pending staging** (see §5 — justification).

### Documentation

- [x] Requirement doc completed (§1–§3).
- [x] **api-definition** semantic line for `ip_address` (activity-log search response).

---

## 5. Test results

### Test run date

- **Backend implementer**: 2026-04-10  
- **QA verification**: 2026-04-10 (this run)

### Verification (per `.cursor/commands/verify.md`)

| Step | Command / check | Result |
|------|-----------------|--------|
| Restart | `./scripts/dev-services.sh backend restart` (project root) | **OK** |
| Backend health | `curl -s http://localhost:9200/api/health` | **200**, JSON `success: true`, `status: OK` |
| DB (optional) | `curl -s http://localhost:9200/api/db/test` → `data.connected` | **true** |
| Frontend | Not in scope for this requirement | **Skipped** (step 3.5 optional for backend-only) |

### Test results

- **TC-01–TC-04 (unit)**: `cd backend && mvn test` — **pass** — **Tests run: 480**, Failures: 0, Errors: 0, Skipped: 0; **BUILD SUCCESS** (QA re-run 2026-04-10T19:57+09:00). Covers: valid XFF + proxy `RemoteAddr`; invalid pattern `172.23.111.*` with fallback to valid `RemoteAddr`; no-headers + direct `RemoteAddr`; alignment **TC-04** via `IpUtilTest` / aspect tests.
- **TC-05 (integration / manual / staging)**: **Not executed in QA environment.** **Justification**: No staging stack or production-mirror proxy available from this workspace; end-to-end header behavior is **partially covered** by TC-02 (same mis-set `X-Forwarded-For: 172.23.111.*` scenario against a servlet mock). **Follow-up for operators**: On staging, authenticate, issue an instrumented request through the real reverse proxy (or deliberate `X-Forwarded-For: 172.23.111.*` against an endpoint that writes activity log), then **`POST /api/activity-log/search`** and assert **`ip_address`** is a **literal** (or null/empty per contract), **never** `172.23.111.*`. Record outcome here when done.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

Record after fix verification (**same document**). Template: `docs/template/ERROR_FIX_RESULT_TEMPLATE.md`.

- **Requirement ID**: `20260410-activity-log-client-ip-wildcard-pattern-stored`
- **Root cause**: **`ActivityLogAspect.getClientIpAddress`** collected **`X-Forwarded-For` / `X-Real-IP` / `RemoteAddr`** into candidates but **did not validate** literals before persist. For a token such as **`172.23.111.*`**, **`isPrivateIp`** failed (non-numeric octet); the **“first non-127.0.0.1 candidate”** branch then **selected that string** and saved it — matching mis-set proxy headers or pasted allowlist tokens. DEBUG diagnostic in the fixed aspect confirms candidates vs resolved value; code review aligns with hypothesis **#1** in §2.
- **Actions taken**: Centralized **validated** resolution in **`IpUtil.getResolvedClientIpForActivityLog`** (aligns with **`getClientIP`** when the primary value is already a valid literal; otherwise walks the same header chain and comma-separated XFF hops for the **first valid literal**, then **`RemoteAddr`**). **`IpUtil.parseValidIpLiteralOrNull`** rejects non-literals (strict IPv4 digits; IPv6 via **`InetAddress`** on `:`-containing tokens only). **`ActivityLogAspect`** delegates to that resolver and logs **one DEBUG line** (no sustained INFO). **`docs/api-definition.md`**: **`ip_address`** semantic note.
- **Result**: **`mvn test`** — 480 tests pass; QA **`./scripts/dev-services.sh backend restart`** → **`GET http://localhost:9200/api/health`** **200** / OK JSON; **`GET /api/db/test`** `connected: true`. TC-05 **manual/staging pending** (documented in §5 with justification).
- **Completed**: 2026-04-10 — backend implementation + unit tests; **2026-04-10** — QA verification (restart, health, DB check, §5/§6 update). §7 Korean final version pending per `DOCUMENT-LANGUAGE-POLICY.md` (Requirements).

---

## 7. Final version (Korean) — add after all verification is complete

### Final Korean summary

- [To be added per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`]

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-10  
**Status**: QA verified (unit + local verify); TC-05 staging manual pending; §7 Korean pending
