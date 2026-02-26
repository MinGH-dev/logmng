# Delegation Improvement Backlog

Small, incremental items derived from the analysis. **One item at a time**; when done, move to IMPROVEMENT-LOG and tick here.

Reference: `ANALYSIS-SIDE-EFFECTS-AND-MITIGATION.md`.

---

## Backlog items

- [ ] **B1** — In `SUBAGENT-DELEGATION.md`: Add one sentence that "Step 2·3 are invoked and results collected by **Main**; Step 4 handoff to implementer may be performed by **Requirements** using that handoff." (Mitigation §2.A)
- [ ] **B2** — In `SUBAGENT-DELEGATION.md` or `AGENT-COLLABORATION-ON-REQUIREMENT.md`: State that the **implementer reports and asks back the agent who delegated** (Main or Requirements). Domain detail: query the owning expert (Contract, UX, DBA, etc.). (Mitigation §3.A)
- [ ] **B3** — In README "서브에이전트 위임 흐름": Add one line that "사용자 진입점은 항상 메인 채팅이며, 세부 진행은 서브에이전트 세션에서 확인할 수 있다." (Mitigation §4.A/C)
- [ ] **B4** — In `AGENT-COLLABORATION-ON-REQUIREMENT.md` handoff rules: Add "Main assembles Step 2·3 outputs into handoff; when Step 4 is via Requirements, Main passes this handoff to Requirements for forwarding to implementer."
- [ ] **B5** — (Optional) In `SUBAGENT-DELEGATION.md`: Add "Two-call option: (1) Requirements: doc only. (2) Requirements: perform Step 4 handoff using doc path and handoff content from Main." (Mitigation §5.A)
- [ ] **B6** — In `DelegationManager.mdc`: Ensure "Scope" table lists only delegation-mgmt and workflow docs; no product agents/skills/commands.

---

## How to use

1. **DelegationManager** (or user) picks one unchecked item.
2. Apply the **minimal** change to the listed file(s).
3. Check the box above and append to `IMPROVEMENT-LOG.md`.
4. Do not batch multiple items unless they are a single logical edit.
