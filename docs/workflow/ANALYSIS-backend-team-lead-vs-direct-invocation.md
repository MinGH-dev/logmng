# Analysis: Backend Team Lead vs Main Direct Invocation

**Purpose**: Compare two delegation models for Step 4 (backend implementation):

- **Model A (Direct)**: Main agent may call **Backend**, **Backend-Auth**, **Backend-ActivityLog**, **Backend-Log** directly, choosing the subagent by scope.
- **Model B (Team lead)**: Main agent calls **Backend (team lead)** only; Backend classifies scope and delegates to Backend-Auth / Backend-ActivityLog / Backend-Log when appropriate.

**Current project design** (per `SUBAGENT-DELEGATION.md`, `CURSOR-SUBAGENTS-DESIGN.md` §5.1): **Model B** is already specified — Main invokes Backend only; Backend may delegate. This document analyses the **difference** between the two models so you can reason about trade-offs and enforce the chosen model.

---

## 1. Summary comparison

| Dimension | Model A: Main → each Backend agent directly | Model B: Main → Backend (team lead) → delegates |
|-----------|---------------------------------------------|-----------------------------------------------|
| **Who chooses scope** | Main agent (must know Auth vs ActivityLog vs Log vs general) | Backend team lead (single place for scope classification) |
| **Handoff count (Main)** | 1..N (one per backend scope; cross-module = multiple Task calls) | 1 (always one Task to Backend) |
| **Build/restart** | Each delegate could run build/restart → risk of multiple restarts | Single build/restart by team lead after all work |
| **§2 변경 파일 목록** | Main or multiple agents must merge; no single owner | Backend aggregates from delegates; single owner |
| **Cross-module requirement** | Main must split work and call 2+ backend subagents, then coordinate | Backend splits and delegates; single coordination point |
| **Consistency (CONSISTENCY-STANDARDS)** | Each implementer must apply; no single enforcer | Team lead applies and enforces in handoffs to delegates |
| **Main agent complexity** | Higher (scope classification + HANDOFF-CHECKLIST per scope) | Lower (one handoff to Backend; scope-agnostic excerpt) |

---

## 2. Differences in detail

### 2.1 Scope classification

- **Model A**: Main must decide whether the requirement touches Auth, ActivityLog, Log, or general backend (health, config, cross-module). That implies Main needs to know file/component mapping (e.g. AuthController → Backend-Auth, UserActivityLog* → Backend-ActivityLog). If the requirement spans two modules (e.g. Auth + ActivityLog), Main must invoke two subagents and later reconcile §2 and build/restart.
- **Model B**: Backend (team lead) receives the full backend-relevant excerpt (§1, §2 backend part, §2.1, contract, §3 TCs). It classifies scope internally and either implements itself or delegates to Backend-Auth / Backend-ActivityLog / Backend-Log with a **scope-specific** handoff. Cross-module work stays inside Backend’s coordination; Main does not need to know module boundaries.

### 2.2 Build and restart

- **Model A**: If Main calls Backend-Auth and Backend-ActivityLog separately, each could run `mvn test` and `./scripts/dev-services.sh backend restart`. That leads to **multiple restarts** and possible race or “who reports to QA?” confusion. To avoid that, rules would need to say “only one of them runs build/restart” or “Main runs build/restart after all return” — which pushes coordination logic back to Main.
- **Model B**: Delegates do **not** run build/restart; they return their change list and results to Backend. Backend runs **one** build and **one** restart after all backend work (own + delegates) is done, then hands off to QA. Single place for “build/restart done” and QA handoff.

### 2.3 §2 변경 파일 목록 aggregation

- **Model A**: Either each delegate updates the requirement doc §2 (risk of overwrite or merge conflict) or Main must merge multiple change lists. No single owner for “final backend file list” in §2.
- **Model B**: Backend (team lead) **aggregates** §2 변경 파일 목록 from all delegates and updates the requirement doc once. Clear single owner; Review and QA see one consistent list.

### 2.4 Handoff content (HANDOFF-CHECKLIST)

- **Model A**: Main must produce **scope-specific** handoffs for Backend-Auth, Backend-ActivityLog, or Backend-Log when calling them directly. That means Main must (1) classify scope, (2) build an excerpt that fits that scope (e.g. only Auth-related §2 and §3 TCs). Checklist (§2.1, contract, CONSISTENCY-STANDARDS) applies per handoff; Main does that work for each call.
- **Model B**: Main builds **one** handoff for Backend (backend-scope excerpt per HANDOFF-CHECKLIST — §1, §2 backend, §2.1, contract, §3 backend TCs, cross-scope, CONSISTENCY-STANDARDS). Backend then produces **scope-specific** handoffs for delegates if it delegates; Backend owns that refinement. Main does not need to know Auth vs ActivityLog vs Log.

### 2.5 Cross-module and “unclear scope”

- **Model A**: For a requirement that touches “auth + activity log” or “general backend + log,” Main must either call multiple backend subagents (and coordinate) or call **Backend** only (general). So Main either grows coordination logic or falls back to Backend for everything unclear — which is close to Model B.
- **Model B**: Any cross-module or unclear scope is handled inside Backend: Backend can implement part itself and delegate part, or delegate to two modules and aggregate. No extra coordination burden on Main.

### 2.6 Consistency and standards

- **Model A**: CONSISTENCY-STANDARDS (naming, error codes, logging, file structure) must be applied by each implementing agent. Main would need to include the same reference in every direct handoff to Backend-Auth/ActivityLog/Log; enforcement is distributed.
- **Model B**: Backend (team lead) applies CONSISTENCY-STANDARDS and passes them in handoffs to delegates. Single enforcer; delegates work within team-lead-defined constraints.

---

## 3. When Model A might be preferred

- **Very small backend**: Only one module (e.g. only Auth); no need for a team lead; Main calling “Backend-Auth” directly is simple.
- **Strict separation**: You want Main to **never** see “Backend” as a catch-all and always pick the narrowest scope. Then Main must be given clear rules and possibly a mapping (e.g. “AuthController → Backend-Auth only”). Trade-off: Main’s responsibility and prompt size increase.
- **Tool constraint**: If for some reason the Backend (team lead) subagent could not invoke Task to delegate (e.g. “subagents cannot call Task”), then Model A would be the only way to use Backend-Auth/ActivityLog/Log. Current Cursor design allows Backend to call Task with another subagent_type.

---

## 4. When Model B (current design) is preferred

- **Single build/restart and single §2 owner**: Avoid multiple restarts and clear ownership of §2 aggregation.
- **Lower Main complexity**: Main does not need to know backend module boundaries or produce multiple scope-specific handoffs.
- **Cross-module and unclear scope**: One coordination point (Backend) for splitting work and aggregating results.
- **Consistency**: One place (Backend) to enforce CONSISTENCY-STANDARDS and pass them to delegates.

This matches the current project choice: **Main invokes Backend only; Backend may delegate** (SUBAGENT-DELEGATION.md §1 Step 4, §3; CURSOR-SUBAGENTS-DESIGN.md §5.1).

---

## 5. Clarification: what is “current” in this project

- **Documented current behavior**: Main invokes **Backend** (team lead) only for Step 4 backend work. Backend may implement directly or delegate to Backend-Auth, Backend-ActivityLog, Backend-Log via Task with scope-specific handoff; Backend aggregates §2 and runs build/restart once (SUBAGENT-DELEGATION.md §1, §2.2, §2.1, §3).
- **If in practice** Main sometimes calls Backend-Auth / Backend-ActivityLog / Backend-Log directly (e.g. because their `subagent_type` exists and rules do not forbid it), that is **Model A** mixed in. To fully align with the documented design (Model B), rules and prompts should state clearly: “For Step 4 backend work, invoke **Backend** only; do not invoke Backend-Auth, Backend-ActivityLog, or Backend-Log directly.”

---

## 6. References

- `docs/workflow/SUBAGENT-DELEGATION.md` §1 (Step 4), §2.1, §2.2, §3
- `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §1.1, §5.1
- `docs/workflow/HANDOFF-CHECKLIST.md` (Backend handoff)
- `docs/workflow/DRYRUN-backend-team-lead-handoff.md` (handoff verification for team-lead model)
