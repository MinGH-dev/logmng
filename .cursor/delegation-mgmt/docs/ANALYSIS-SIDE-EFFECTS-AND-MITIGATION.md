# Analysis: Step 4 via Requirements — Side Effects and Mitigation

When **implementation (Step 4)** is always delegated **through Requirements** (instead of Main directly calling Frontend/Backend/DB), the following side effects and mitigation strategies apply. Use this document when proposing or applying delegation improvements.

---

## 1. Requirements role expansion (역할 비대화)

**Effect**: Requirements becomes both "requirement doc author" and "orchestrator" (deciding and calling Step 2·3·4).

**Mitigation**

| Option | Action |
|--------|--------|
| **A (recommended)** | Limit Requirements to "doc + **one** Step 4 handoff". Keep "whether Step 2·3 are needed" and "calling experts" with **Main**. Document: "Step 2·3 invocation = Main; Step 4 handoff = Requirements." |
| **B** | Introduce a dedicated **Orchestrator** subagent; Requirements only writes the doc; Orchestrator runs Step 2·3 and Step 4. |
| **C** | Requirements only adds "recommended implementer and handoff text" (e.g. in doc or output). **Main** still performs the actual mcp_task(Step 4) using that handoff. |

---

## 2. Step 2·3 result ownership

**Effect**: Unclear who calls experts and who assembles the handoff for Step 4.

**Mitigation**

| Option | Action |
|--------|--------|
| **A (recommended)** | **Main** calls Step 2·3, collects results, and passes a single handoff (doc path + Security/Contract/UX outputs) to **Requirements**. Requirements forwards that handoff to the implementer(s) only. Document explicitly: "Step 2·3 call/collect = Main; Step 4 handoff = Requirements." |
| **B** | Requirements decides and calls Step 2·3, then assembles handoff. Document: "Requirements: doc + Step 2·3 + Step 4." (Increases Requirements load.) |
| **C** | Step 2·3 outputs are written to shared artifacts (e.g. requirement doc §2.1, specs). One designated agent (Main or Requirements) reads them and builds the handoff; document which one. |

---

## 3. Implementer’s "caller" (호출 주체) clarity

**Effect**: Frontend/Backend/DB may not know whom to ask (Main vs Requirements) for follow-up or clarification.

**Mitigation**

| Option | Action |
|--------|--------|
| **A (recommended)** | Define "caller = who gave the task". If Requirements delegated Step 4, implementer reports and asks **Requirements**. If Main delegated, implementer reports to **Main**. Document and add a short note to SUBAGENT-DELEGATION or agent prompts. |
| **B** | Keep §1.3 "query experts directly" for domain questions; use "caller" only for progress and task closure. Document a small matrix: "task/status → caller; domain detail → expert." |
| **C** | Add a table in SUBAGENT-DELEGATION: "Implementer may query: requirement content → Requirements (or Main); API/schema/UX → Contract/DBA/UX." |

---

## 4. Main agent role and user awareness

**Effect**: Main seems to "do less"; users may be unsure where to follow progress (Main vs Requirements chat).

**Mitigation**

| Option | Action |
|--------|--------|
| **A (recommended)** | Keep **Main as single entry point**. Main runs "Requirements → (Step 2·3 if needed) → request Requirements to perform Step 4 handoff" and posts a **one-line summary** in Main chat (e.g. "Step 4 delegated by Requirements to Frontend; see Requirements session for details"). |
| **B** | Subagent completion results return to Main; Main chat shows "Requirements done", "Frontend done, QA delegated", etc., so the user can follow flow in one place. |
| **C** | In README/SUBAGENT-DELEGATION, state: "User always starts in Main chat; Main coordinates; details may appear in subagent sessions." |

---

## 5. Session and re-invocation

**Effect**: One long Requirements session (doc + handoff) vs two invocations (doc then handoff) — context size and duplication.

**Mitigation**

| Option | Action |
|--------|--------|
| **A (recommended)** | **Two invocations**: (1) Main → Requirements "write requirement doc" (output: doc path). (2) Main → Requirements "using this doc and handoff [content], perform Step 4 delegation." Second call is short (path + handoff). Requirements only does "read doc + delegate" in the second call. |
| **B** | One call: Requirements output includes "suggested implementer + handoff text". **Main** then calls Frontend/Backend/DB with that handoff. No extra Requirements session; "via Requirements" means "handoff **content** from Requirements", not "Requirements calls implementer". |
| **C** | One long session: Requirements writes doc then immediately performs Step 4 handoff. Reserve for simple cases; document that complex flows may use two invocations. |

---

## Summary matrix (for DelegationManager)

| # | Side effect | Recommended mitigation |
|---|-------------|------------------------|
| 1 | Requirements role expansion | Requirements = doc + single Step 4 handoff; Step 2·3 stay with Main. |
| 2 | Step 2·3 result ownership | Main collects expert outputs; passes handoff to Requirements → implementer. |
| 3 | Implementer caller clarity | Caller = who delegated; document "report/ask back to caller; domain → expert." |
| 4 | Main role / user awareness | Main = entry point; subagent results summarized in Main chat. |
| 5 | Session / re-invocation | Prefer two short Requirements calls (doc, then handoff) or handoff content from Requirements + Main calls implementer. |

When adding improvements, pick **one** mitigation item at a time and apply it in workflow docs or delegation-mgmt docs only.
