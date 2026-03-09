# Dry-run: Backend team lead and handoff verification

**Purpose**: Verify that the Backend team-lead model (Main → Backend only; Backend may delegate to Backend-Auth/ActivityLog/Log; single build/restart; §2 aggregation) and updated HANDOFF-CHECKLIST (including CONSISTENCY-STANDARDS) produce correct handoff prompts. **No code changes.** Prompts only.

**Date**: 2026-03-09

---

## 1. Virtual requirement

**Title**: Activity log API: add department filter and align error codes with CONSISTENCY-STANDARDS

- **§1 (summary)**: User wants to filter activity log by department. Backend must add a department parameter to the activity log search API and ensure new/updated error responses follow project error-code conventions.
- **Scope**: Backend (activity-log module); touches API + error codes → Contract + CONSISTENCY applicable.
- **§2 Backend (excerpt)**: Extend `UserActivityLogSearchRequest` (or equivalent) with optional `departmentId`; filter in `UserActivityLogService`; return 400 with code `INVALID_INPUT` when department is invalid; document in api-definition.md.
- **§2.1 Security**: N/A (no PII/decryption/access change).
- **§3 (Backend TCs)**: TC1 Unit: search with departmentId filters correctly. TC2 Unit: invalid departmentId returns 400 and INVALID_INPUT. TC3 Integration: GET with department param returns 200 and filtered list.

---

## 2. Simulated handoff chain

### 2a. Requirements handoff (Main → Requirements)

**Task invocation** the main agent would send:

- **subagent_type**: `Requirements`
- **description**: "Author requirement doc: activity log department filter"
- **prompt** (excerpt):
  - "Author a requirement document per `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md`: parallel input from experts, orchestrate into §1·§2, run change target verification per `REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`, finalize §3 with Scope tags. User request: [Activity log API: add department filter and align error codes with CONSISTENCY-STANDARDS]. Output: requirement doc at `docs/requirements/yyyyMMdd-activity-log-department-filter.md`."

**Checks**:
- [x] Main does **not** author §1·§2·§3 (per agent-collaboration.mdc Step 1).
- [x] Prompt instructs Requirements to follow REQUIREMENTS-AUTHORING-WORKFLOW.md (hybrid: skills + codebase + selective experts).

---

### 2b. Backend handoff (Main → Backend, team lead)

**Task invocation** the main agent would send:

- **subagent_type**: `Backend`
- **description**: "Implement activity log department filter and error codes"
- **prompt** (full, scope-specific excerpt):

```
Requirement doc: docs/requirements/yyyyMMdd-activity-log-department-filter.md

§1 summary: Add department filter to activity log search API; align error codes with project standards.

§2 Backend:
- Extend UserActivityLogSearchRequest with optional departmentId.
- Filter in UserActivityLogService by department when provided.
- Return 400 with code INVALID_INPUT for invalid departmentId.
- Update docs/api-definition.md for the changed request/response and error code.

§2.1 Security: Not applicable (no PII/decryption/access change).

Contract/spec: GET /api/user-activity-log (or equivalent) request query params per docs/contract.md and specs; error code INVALID_INPUT per docs/api-definition.md §11.

§3 Backend TCs:
- TC1 (Unit): Search with departmentId filters correctly.
- TC2 (Unit): Invalid departmentId returns 400 and INVALID_INPUT.
- TC3 (Integration): GET with department param returns 200 and filtered list.

Cross-scope: Frontend will call this API with optional departmentId query param; implement to contract.

CONSISTENCY-STANDARDS: This change touches error codes and API request shape. Apply docs/workflow/CONSISTENCY-STANDARDS.md for naming, error codes, and logging.

Doc–code sync: Update docs/api-definition.md (and contract/specs if needed) for new param and INVALID_INPUT usage in same work. See docs/workflow/DOC-CODE-SYNC.md.

Task: Implement the Backend changes. You may implement directly or delegate to Backend-ActivityLog via Task with a scope-specific handoff per HANDOFF-CHECKLIST. If you delegate, aggregate §2 변경 파일 목록 from the delegate, run build and restart once after all backend work is done, then hand off to QA.
```

**HANDOFF-CHECKLIST (Backend) verification**:

| Item | Present in prompt | Pass |
|------|-------------------|------|
| §1 summary | Yes | ✓ |
| §2 Backend subsection | Yes | ✓ |
| §2.1 Security (if applicable) | Yes (N/A stated) | ✓ |
| Contract/spec | Yes | ✓ |
| §3 Backend TCs | Yes | ✓ |
| Cross-scope | Yes | ✓ |
| **CONSISTENCY-STANDARDS** | Yes (reference + “apply for error codes”) | ✓ |
| Doc–code sync | Yes | ✓ |

- [x] Prompt uses **scope-specific excerpts**, not the full requirement doc.
- [x] Main invokes **Backend** only (no direct Main → Backend-ActivityLog for this flow).

---

### 2c. Backend → Backend-ActivityLog (delegate handoff, optional)

If Backend (team lead) decides to delegate activity-log work to Backend-ActivityLog, Backend would send:

- **subagent_type**: `Backend-ActivityLog`
- **description**: "Activity log department filter and INVALID_INPUT"
- **prompt** (scope-specific, per HANDOFF-CHECKLIST):

```
Requirement doc: docs/requirements/yyyyMMdd-activity-log-department-filter.md

§1 summary: Add department filter to activity log search API; align error codes.

§2 Backend (Activity log scope): Extend UserActivityLogSearchRequest with optional departmentId; filter in UserActivityLogService; return 400 with INVALID_INPUT for invalid departmentId. Change file list (tentative): UserActivityLogSearchRequest, UserActivityLogService, UserActivityLogController, api-definition.md.

§3 TCs for this scope: TC1 Unit (departmentId filter), TC2 Unit (invalid → 400 INVALID_INPUT), TC3 Integration (GET with param).

Contract/spec: GET /api/user-activity-log query params and INVALID_INPUT per docs/contract.md and docs/api-definition.md §11.

CONSISTENCY-STANDARDS: Apply docs/workflow/CONSISTENCY-STANDARDS.md for error code and logging.

Doc–code sync: Update docs/api-definition.md for new param and error in same work.

Task: Implement only activity-log/statistics scope. Do NOT run build or restart — return the list of files you changed so Backend (team lead) can aggregate §2 and run build/restart once.
```

**Checks**:
- [x] Delegate handoff is **scope-specific** (activity-log only).
- [x] Delegate is told **not** to run build/restart and to **return changed file list**.
- [x] CONSISTENCY-STANDARDS and Doc–code sync included.

---

### 2d. QA handoff (Main → QA, after Backend completes)

**Task invocation** (after Backend has run build/restart and requested QA):

- **subagent_type**: `QA`
- **description**: "Verify activity log department filter"
- **prompt** (excerpt):

```
Requirement doc: docs/requirements/yyyyMMdd-activity-log-department-filter.md

§1 summary: Add department filter to activity log search API; align error codes.

§3 (full test case list): [TC1, TC2, TC3 as above; plus any Manual/Frontend TCs if present].

Build/restart: Confirmed. Backend reported: Build: cd backend && mvn test — exit 0. Restart: ./scripts/dev-services.sh backend restart — done. QA verification requested.

Update §5 (and §6 if error fix) in the requirement doc. Perform verification per .cursor/commands/verify.md.
```

**HANDOFF-CHECKLIST (QA) verification**:

| Item | Present | Pass |
|------|---------|------|
| §1 summary + §3 | Yes | ✓ |
| Build/restart confirmation | Yes | ✓ |
| Requirement doc path for §5/§6 | Yes | ✓ |

---

## 3. Verification table (rules and documents)

| Rule / Document | Check | Pass? |
|-----------------|-------|-------|
| agent-collaboration.mdc Step 1 gate | Main does not author §1·§2·§3 | ✓ |
| agent-collaboration.mdc §3 gate | §3 exists before Step 4 | ✓ |
| REQUIREMENTS-AUTHORING-WORKFLOW.md | Hybrid consultation (skills + tools + selective experts) | ✓ |
| HANDOFF-CHECKLIST.md Backend | All 8 items (§1, §2, §2.1, contract, §3, cross-scope, **CONSISTENCY-STANDARDS**, Doc–code sync) | ✓ |
| HANDOFF-CHECKLIST.md QA | All 3 items (§1+§3, build/restart, doc path) | ✓ |
| SUBAGENT-DELEGATION Step 4 | Main invokes **Backend** only for backend work | ✓ |
| SUBAGENT-DELEGATION §2.1 | Backend (team lead) runs build/restart once; delegates do not | ✓ |
| SUBAGENT-DELEGATION §3 | Backend aggregates §2 변경 파일 목록 from delegates | ✓ |
| backend.md team lead | Backend may delegate to Backend-Auth/ActivityLog/Log; handoff per HANDOFF-CHECKLIST; CONSISTENCY applied | ✓ |
| CONTEXT-QUALITY (scope-specific) | Backend prompt uses excerpts, not full doc | ✓ |

---

## 4. Result

**All checks passed.** The Backend team-lead model and updated handoff checklist (including CONSISTENCY-STANDARDS) produce consistent, rule-compliant handoff prompts. Main invokes only Backend; Backend may delegate to Backend-ActivityLog with a scope-specific handoff; Backend aggregates §2 and runs build/restart once before QA.

**Optional follow-up**: Run the same dry-run with a requirement that triggers **Security** (e.g. PII in activity log) to confirm §2.1 is included in the Backend handoff.
