# Analysis: Requirement doc and agent strengthening (design-standards feedback)

**Context**: The requirement document `docs/requirements/20260311-activity-log-statistics-design-standards.md` was produced from a user request to align specific screens with design standards. This analysis uses the provided feedback to improve (1) the requirement document itself, (2) the requirement template and workflow, and (3) agent/checklist rules so future requirement docs and implementations follow the same quality bar.

**Reference**: User feedback (총평, 잘된 점, 보완이 필요한 점, 결론, 우선 수정 권장 3가지, 바로 추가하면 좋은 문장).

---

## 1. Summary of feedback

### 1.1 Overall assessment

- The requirement doc **generally applied the intended practices well**.
- Three strengths:
  - Design docs are used as a **single source of truth**.
  - §1 and §2 **explicitly list** design docs the Frontend implementer must read.
  - **“Do not implement when standard is undefined”** is present; user should be informed and standard defined first.

### 1.2 What worked well

| Item | Description |
|------|-------------|
| Standard reference structure | forms-and-filters, search-fields-by-screen, search-field-definition-items, css-standard-and-exceptions, UX-REDESIGN-activity-log-statistics-search are tied as implementation baseline. |
| Undefined-standard principle | §2 Implementation note states: if design docs lack a needed standard, do not implement; inform user and request standard definition first. |
| Concrete UI and targets | row1/row2, Search/Reset placement, group title above fields, max-width 1400px, compact spacing, scope=self are explicit. |
| Test coverage | TC-01–TC-09 cover layout, a11y, Reset, scope=self, cross-screen consistency. |

### 1.3 Gaps to address

| Gap | Issue | Recommended direction |
|-----|--------|------------------------|
| **1. Undefined-standard response** | “Inform user” is stated but **how** to respond is not enforced (what to list, why needed, draft standard, request feedback). | Add an explicit **response pattern** in §2 Implementation note and in REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4; reuse sentence from feedback. |
| **2. Requirement vs standard definition** | Some values (max-width 1400px, compact spacing, row1/row2, no collapsible) read as **defined in the requirement** rather than “refer to design doc”. | Keep requirement as **referencing** design docs; ensure numeric/structural values are **sourced from** design docs; separate “design standard” vs “product requirement” vs “TBD” in structure. |
| **3. “Actual files changed” wording** | Section reads like **implementation output** (actual files changed). | Use **“Planned change file list”** / **“Expected change targets”** in requirement phase; implementing agent **confirms or amends** on completion. |
| **4. Document tone** | Phrases like “No change”, “already present”, “confirmed by implementing agent” sound like **implementation report**. | Use requirement tone: **must**, **verify**, **align**, **confirm** (e.g. “Verify no change required”, “Confirm … present”). |
| **5. Optional / unconfirmed** | Design standard vs product requirement vs unconfirmed are mixed. | Clearly separate: **design standard** (from design docs), **product requirement** (explicit), **not yet confirmed** (optional/TBD, implement only if confirmed). |

---

## 2. Recommended actions

### 2.1 Priority 1: Undefined-standard response (rule + doc)

**Goal**: When a standard is missing or ambiguous, the implementer **must not** infer or hardcode; they must follow a fixed response pattern.

**Actions**:

1. **Add the following sentence** to the **Implementation note** in requirement docs that follow pattern §2.4 (search/filter UI), and to `REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4 row “Implementation note for Frontend”:

   > If any required standard for layout, field sizing, spacing, icon usage, label placement, or control semantics is not defined or is ambiguous in the design docs, the implementer must not infer or hardcode a solution. The implementer must first inform the user of the undefined standard items, explain why each is needed, propose a recommended standard draft, and request feedback so the standard can be explicitly defined before implementation proceeds.

2. **Reference**: `docs/design/ux-frontend-standard-principles.md` §2, §10, §11 already define “inform user, do not implement”; this sentence makes the **response format** (list items, explain, propose draft, request feedback) explicit in the requirement and handoff.

3. **Handoff**: In `HANDOFF-CHECKLIST.md` Frontend § “Standard-first”, keep or reinforce the same instruction so Frontend receives it in every search/filter handoff.

### 2.2 Priority 2: “Actual files changed” → “Planned / Expected change targets”

**Goal**: Requirement docs describe **planned/expected** scope; only after Step 4 do we have “actual files changed”.

**Actions**:

1. **Template** (`REQUIREMENT_TEMPLATE.md`):
   - Section title: keep **“Change file list”** or rename to **“Planned change file list”** (or **“Expected change targets”**).
   - Note text: change from “(Tentative. Implementing agent (Step 4) confirms or updates with actual files changed.)” to:
     - **“(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)”**
   - So the **requirement phase** is clearly “planned/expected”; “actual” is only after implementation.

2. **Workflow** (`REQUIREMENTS-AUTHORING-WORKFLOW.md` §1.2):
   - State that §2 lists **planned** (or **expected**) change targets; the implementing agent **confirms or amends** with the actual set of files changed.
   - Avoid using “Actual files changed” as the **section name** in the requirement doc; reserve that for the implementing agent’s update.

3. **This requirement doc** (`20260311-activity-log-statistics-design-standards.md`):
   - Replace the parenthetical under “Change file list” with the new note above.
   - Optionally add a short heading or subtitle: “Planned change file list (expected targets)”.

### 2.3 Priority 3: Requirement doc tone (no implementation-report phrasing)

**Goal**: §2 and change file list use **requirement/verification** language, not implementation-complete language.

**Actions**:

1. **In the same requirement doc**, in the change file list:
   - “No change (… already present)” → **“Verify no change required (… already present)”** or **“Confirm …; no structural change.”**
   - “already present” → **“verify present”** or **“confirm … present.”**
   - “Comment added; … already present” → **“Verify or add comment; confirm aria-invalid and aria-describedby for date range error; use error id … for association.”**
   - “No change; components reference …” → **“Verify no change; components must reference … only.”**

2. **Template**: Add a short guideline in §2 “Change file list”: “Use requirement tone: must, verify, align, confirm. Avoid implementation-complete phrasing (e.g. ‘No change’, ‘already present’, ‘confirmed by implementing agent’) in the authored requirement; reserve those for §5 or post-implementation updates.”

3. **Future requirement docs**: Requirements subagent should prefer **verify/confirm/must** in §2 and change file list; “actual files changed” and “confirmed by” stay in §5 or in the implementing agent’s update to §2.

---

## 3. Role of requirement vs design doc

- **Requirement doc**: States **what** must be achieved and **where** to read the standard (which design docs); it should **not** become the place where new layout/spacing/sizing standards are **first** defined.
- **Design docs**: Are the **single source** for numeric and structural standards (e.g. 1400px, 8–12px, row1/row2). The requirement doc **references** them and may quote ranges for traceability; if a value appears only in the requirement and not in a design doc, consider adding it to the design doc so the requirement stays “reference-only”.
- **Optional / unconfirmed**: Use explicit labels (e.g. “Optional field constraints (implement only if product confirms)”) and, where possible, a short “Design standard | Product requirement | Not yet confirmed” split in §2 so implementers know what is mandatory vs conditional.

---

## 4. Checklist and agent updates

| Asset | Update |
|-------|--------|
| **REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md** §2.4 | Add the full “If any required standard …” sentence to the “Implementation note for Frontend” cell so every §2.4 requirement gets it. |
| **REQUIREMENT_TEMPLATE.md** | (1) Implementation note guidance for §2.4: include undefined-standard sentence when pattern applies. (2) Change file list: “Planned at authoring …” note and tone guideline (verify/confirm, avoid “No change”/“already present” in authored §2). |
| **REQUIREMENTS-AUTHORING-WORKFLOW.md** §1.2 | Use “planned/expected change targets” and “confirms or amends”; avoid “actual files changed” as the authoring-time section name. |
| **HANDOFF-CHECKLIST.md** Frontend § Standard-first | Ensure the handoff text matches the same undefined-standard response (list, explain, propose draft, request feedback). |
| **Requirements subagent** | When authoring §2.4 requirements: (1) include the undefined-standard sentence in Implementation note; (2) use planned/expected change list wording and requirement tone in §2. |
| **Frontend (implementer)** | When design doc is missing a standard: follow the response pattern (list undefined items, explain why needed, propose draft, request user feedback); do not implement. |

---

## 5. Immediate edits applied (or to apply)

1. **`docs/requirements/20260311-activity-log-statistics-design-standards.md`**
   - §2 Implementation note: append the “If any required standard …” sentence.
   - §2 “Change file list”: replace parenthetical with “(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)”
   - Change file list entries: rephrase “No change”/“already present” to “Verify …”/“Confirm …” as in §2.3 above.

2. **`docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`**
   - §2.4 “Implementation note for Frontend”: add the same “If any required standard …” sentence (or reference it and paste in place).

3. **`docs/template/REQUIREMENT_TEMPLATE.md`**
   - Change file list note: use “Planned at authoring …” wording.
   - Optional: add one line on requirement tone (verify/confirm, avoid implementation-complete phrasing in authored §2).

4. **`docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md`** §1.2
   - Use “planned/expected” for the list authored by Requirements; “actual files changed” only when implementing agent updates after Step 4.

---

## 6. One-line assessment (from feedback)

**“방향은 잘 적용됐고, 실전 투입도 가능하지만, ‘미정의 표준 대응 방식’과 ‘문서 톤 일관성’을 조금 더 다듬으면 훨씬 강한 요구사항 문서가 됩니다.”**

This analysis and the recommended edits above are intended to strengthen the undefined-standard response and document tone so that both this requirement and future ones are clearer and more enforceable.
