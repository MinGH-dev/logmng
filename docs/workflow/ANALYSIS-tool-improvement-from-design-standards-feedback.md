# Analysis: Tool improvement from design-standards requirement feedback

**Context**: The requirement document `docs/requirements/20260311-activity-log-statistics-design-standards.md` was created from a user request to improve specific screens to align with design standards. This analysis uses the provided feedback to determine **how to improve the project’s “tools”** (templates, workflow, checklists, rules, skills, design docs) so that future requirement authoring and implementation consistently follow the same quality bar.

**Related**: `docs/workflow/ANALYSIS-requirement-doc-design-standards-feedback.md` (feedback summary and recommended actions). This document focuses on **which tools to change** and **how**.

---

## 1. Scope of “tools”

| Tool | Path / location | Role |
|------|------------------|------|
| **Requirement template** | `docs/template/REQUIREMENT_TEMPLATE.md` | Structure and wording of every new requirement doc (§1–§7, change file list, tone). |
| **Requirements authoring workflow** | `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` | How Requirements subagent gathers input, orchestrates §1·§2, and runs change-target verification. |
| **Change target checklist** | `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` | Mandatory verification before §2 final; pattern §2.4 (search/filter) and Implementation note. |
| **Handoff checklist** | `docs/workflow/HANDOFF-CHECKLIST.md` | What to include in Frontend (and other) handoffs; design doc implementation, CSS standard, standard-first. |
| **UX standard-first principles** | `docs/design/ux-frontend-standard-principles.md` | Single reference for “standard missing → do not implement; inform user; propose draft; request feedback.” |
| **Requirement-doc skill** | `.cursor/skills/requirement-doc/SKILL.md` | Guidance for Requirements subagent (and main agent when authoring); template ref, §2 tentative list. |
| **UX / standard-first rules** | `.cursor/rules/ux-frontend-standard-first.mdc`, `.cursor/rules/search-filter-form-design.mdc` | Agent behavior when editing frontend/design; “inform user when standard undefined.” |

Design docs (`forms-and-filters.md`, `search-fields-by-screen.md`, etc.) are **referenced** by these tools; improving them is separate (e.g. ensuring numeric values live there, not only in requirement docs).

---

## 2. Feedback → tool mapping

Each feedback item is mapped to the tools that should be updated so the behavior becomes repeatable.

### 2.1 “미정의 표준 대응” 강화 (Undefined-standard response)

**Feedback**: 취지는 있지만 Cursor가 실제로 어떻게 응답해야 하는지 강제되지 않음. 다음 4가지를 명시해야 함: (1) 어떤 표준이 미정의인지 나열, (2) 왜 필요한지 설명, (3) 권장 표준안 제시, (4) 표준을 먼저 확정하고 진행할지 사용자 피드백 요청.

| Tool | Current state | Improvement |
|------|----------------|-------------|
| **REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md** §2.4 | Implementation note references ux-frontend-standard-principles; long sentence in ANALYSIS. | **Embed the full “If any required standard …” sentence** in the §2.4 “Implementation note for Frontend” cell so every §2.4 requirement carries it. |
| **HANDOFF-CHECKLIST.md** Frontend § Standard-first | Short line: “inform the user and request standard definition first per …” | **Replace or extend** with the same four-step response pattern: list undefined items, explain why needed, propose recommended draft, request feedback before implementation. |
| **ux-frontend-standard-principles.md** §2 | Already has table and “Required response format” (list, explain, do not implement, optionally propose). | **Optional**: Add one explicit sentence for implementers: “The implementer must … inform the user of the undefined standard items, explain why each is needed, propose a recommended standard draft, and request feedback so the standard can be explicitly defined before implementation proceeds.” (Align with the English sentence from the feedback.) |
| **REQUIREMENT_TEMPLATE.md** §2 | §2.4 guidance mentions Implementation note “read and apply from design docs”. | **Add**: When pattern §2.4 applies, the Implementation note must include the full undefined-standard sentence (or reference ux-frontend-standard-principles.md §2 and paste the sentence in the requirement doc). |
| **requirement-doc skill** | No explicit “undefined standard response” wording. | **Add one bullet**: When authoring §2.4 requirements, include in Implementation note the full “undefined standard” response rule (list, explain, propose draft, request feedback); see REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4 and ux-frontend-standard-principles.md §2. |

**Suggested sentence to reuse** (from feedback):

```md
If any required standard for layout, field sizing, spacing, icon usage, label placement, or control semantics is not defined or is ambiguous in the design docs, the implementer must not infer or hardcode a solution. The implementer must first inform the user of the undefined standard items, explain why each is needed, propose a recommended standard draft, and request feedback so the standard can be explicitly defined before implementation proceeds.
```

---

### 2.2 “Actual files changed” → “Planned / Expected change targets”

**Feedback**: 요구사항 문서에서는 “Actual files changed”가 아니라 “Planned change file list” / “Expected change targets” 표현 사용.

| Tool | Current state | Improvement |
|------|----------------|-------------|
| **REQUIREMENT_TEMPLATE.md** §2 “Change file list” | Title “Change file list”; note “(Tentative. Implementing agent (Step 4) confirms or updates with actual files changed.)” | **Title**: Use **“Planned change file list (expected change targets)”** (or keep “Change file list” and add subtitle “(Planned at authoring. Expected change targets.)”). **Note**: “(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)” — remove “actual files changed” from the requirement-phase description. |
| **REQUIREMENTS-AUTHORING-WORKFLOW.md** §1.2 | Says implementing agent “updates … with the **actual** list of files”. | **Keep** the fact that Step 4 updates the list, but **clarify**: §2 as authored contains **planned/expected** change targets only; the implementing agent **confirms or amends** (and may append “actual files changed” in §2 or §5). Avoid “Actual files changed” as the **section name** in the requirement doc at authoring time. |
| **requirement-doc skill** | Refers to “변경 파일 목록” as “tentative”; implementing agent “confirms or updates”. | **Add**: In the requirement doc §2, the list is “planned change file list (expected change targets)”; do not use “Actual files changed” as the section heading when authoring. |

---

### 2.3 요구사항 문서 톤과 구현 결과 문서 톤 분리 (Document tone)

**Feedback**: “No change”, “already present”, “confirmed by implementing agent”는 구현 완료 보고서 톤. 요구사항 문서는 `must`, `verify`, `align`, `confirm` 톤으로 통일.

| Tool | Current state | Improvement |
|------|----------------|-------------|
| **REQUIREMENT_TEMPLATE.md** §2 | No explicit tone guideline for the change file list. | **Add** a short guideline in §2 “Change file list”: “Use requirement tone: **must**, **verify**, **align**, **confirm**. Avoid implementation-complete phrasing (e.g. ‘No change’, ‘already present’, ‘confirmed by implementing agent’) in the authored requirement; reserve those for §5 or post-implementation updates.” |
| **requirement-doc skill** | No tone guideline. | **Add**: When authoring §2 and the change file list, use requirement tone (must, verify, align, confirm); avoid “No change”, “already present”, “confirmed by” in the initial draft. |
| **REQUIREMENTS-AUTHORING-WORKFLOW.md** | No tone guideline. | **Optional**: In step 5 (Orchestrate) or 6 (Finalize), add one line: “Use requirement tone in §2 and change file list (must, verify, confirm); avoid implementation-report phrasing.” |

---

### 2.4 요구사항 vs 표준 정의 문서 역할 분리 (Requirement vs standard definition)

**Feedback**: 일부 값(max-width 1400px, compact spacing, row1/row2, no collapsible)이 요구사항 문서에서 “여기서 확정”하는 느낌. 원칙상 표준은 디자인 문서에 있어야 함.

| Tool | Current state | Improvement |
|------|----------------|-------------|
| **REQUIREMENT_TEMPLATE.md** §1 | Note on design doc references and §2.4. | **Add** one sentence: “Numeric and structural values (e.g. max-width, spacing, row layout) must be **sourced from** design docs; the requirement doc **references** them and may quote for traceability. If a value appears only here and not in a design doc, consider adding it to the design doc so the requirement stays reference-only.” |
| **REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md** §2.4 | Design doc references and Implementation note. | **Optional**: In the same row or a new bullet, add: “Requirement §2 must not be the **first** place where layout/spacing/sizing standards are defined; they must be in design docs and only referenced in the requirement.” |
| **requirement-doc skill** | Emphasizes template and workflow. | **Add**: For search/filter or layout alignment, ensure §2 **references** design docs for numeric/structural values; do not define new standards only in the requirement doc. |

---

### 2.5 Optional / 미확정 구분 (Design standard vs product requirement vs unconfirmed)

**Feedback**: 디자인 표준 / 화면별 제품 요구사항 / 아직 미확정 사항을 구분해서 써야 함.

| Tool | Current state | Improvement |
|------|----------------|-------------|
| **REQUIREMENT_TEMPLATE.md** §2 | No explicit split for “design standard | product requirement | not yet confirmed”. | **Add** optional guideline: “Where applicable, separate: **Design standard** (from design docs), **Product requirement** (explicitly agreed), **Not yet confirmed** (optional/TBD; implement only if product confirms). Use labels such as ‘Optional field constraints (implement only if product confirms)’.” |
| **REQUIREMENTS-AUTHORING-WORKFLOW.md** | No mention of this split. | **Optional**: In step 5 (Orchestrate), add: “For optional or unconfirmed items, mark clearly (e.g. ‘implement only if product confirms’) so implementers do not treat them as mandatory.” |

---

## 3. Implementation order (도구 개선 순서)

Recommended order so that dependencies are clear and changes are consistent.

| Order | Tool | Change (summary) |
|-------|------|------------------|
| 1 | **REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md** | §2.4: Paste the full “If any required standard …” sentence into the Implementation note for Frontend. |
| 2 | **REQUIREMENT_TEMPLATE.md** | (1) §2: “Planned change file list (expected change targets)” + note “Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.” (2) §2: Tone guideline (must/verify/align/confirm; avoid “No change”/“already present” in authored §2). (3) §1: One sentence on sourcing numeric/structural values from design docs. (4) §2: Optional “Design standard | Product requirement | Not yet confirmed” guideline. (5) §2.4: When pattern §2.4 applies, Implementation note must include the undefined-standard sentence. |
| 3 | **HANDOFF-CHECKLIST.md** | Frontend § Standard-first: Extend with the four-step response (list undefined, explain why, propose draft, request feedback) or paste the same sentence as in the checklist. |
| 4 | **REQUIREMENTS-AUTHORING-WORKFLOW.md** | §1.2: Use “planned/expected change targets”; “actual files changed” only when Step 4 updates. Optional: tone guideline in Finalize; optional/TBD labeling in Orchestrate. |
| 5 | **requirement-doc skill** | Add: planned change file list wording; requirement tone; undefined-standard sentence for §2.4; reference design docs for numeric/structural values. |
| 6 | **ux-frontend-standard-principles.md** | Optional: Add the one-sentence implementer obligation (inform, explain, propose draft, request feedback) in §2 so it matches the sentence used in checklists and handoff. |

Rules (`.cursor/rules/ux-frontend-standard-first.mdc`, `search-filter-form-design.mdc`) already point to `ux-frontend-standard-principles.md`; once the principles doc and handoff checklist carry the full response pattern, the rules do not need structural change unless we want to paste the sentence there too.

---

## 4. Verification after tool changes

After applying the above:

1. **New requirement doc (e.g. search/filter alignment)**  
   - §2 uses “Planned change file list (expected change targets)” and the new note.  
   - §2 and change list use must/verify/confirm (no “No change”/“already present” in authored text).  
   - When §2.4 applies, Implementation note contains the full undefined-standard sentence.  
   - Optional/TBD items are labeled (e.g. “implement only if product confirms”).

2. **Handoff**  
   - Frontend handoff for a §2.4 requirement includes the Standard-first instruction with the four-step response (or the full sentence).

3. **Implementer behavior**  
   - When a standard is missing, implementer (or agent) responds with: list undefined items, explain why needed, propose draft, request feedback; does not implement first.

---

## 5. Summary

| Feedback item | Primary tools to update |
|---------------|-------------------------|
| 미정의 표준 대응 (response pattern) | REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4, HANDOFF-CHECKLIST Frontend, REQUIREMENT_TEMPLATE §2.4, requirement-doc skill, (optional) ux-frontend-standard-principles §2 |
| “Actual files changed” → Planned/Expected | REQUIREMENT_TEMPLATE §2, REQUIREMENTS-AUTHORING-WORKFLOW §1.2, requirement-doc skill |
| 문서 톤 (requirement vs implementation report) | REQUIREMENT_TEMPLATE §2, requirement-doc skill, (optional) REQUIREMENTS-AUTHORING-WORKFLOW |
| 요구사항 vs 표준 정의 역할 | REQUIREMENT_TEMPLATE §1, (optional) REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4, requirement-doc skill |
| Optional / 미확정 구분 | REQUIREMENT_TEMPLATE §2, (optional) REQUIREMENTS-AUTHORING-WORKFLOW |

Applying the implementation order in §3 will align all tools with the feedback and make the “미정의 표준 대응”, “Planned change file list”, requirement tone, and design-doc-as-source principles repeatable for future requirement docs and implementations.
