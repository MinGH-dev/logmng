# Agent Skill & Document Improvement Design

Long-term design for improving Agent behavior through domain skills and document restructuring. This document serves as a roadmap for gradual implementation.

**Status**: Draft  
**Owner**: Project maintainer  
**Last updated**: 2025-03-03

---

## 1. Executive Summary

### 1.1 Goal

Improve Agent answer quality and token efficiency by:

1. **Domain skills** as a navigation layer — Agent reads skill first; skill points to relevant documents.
2. **Documents remain the single source of truth** — No duplication; skills reference, not copy.
3. **Document chunking by domain** — Load only what is needed per question type.

### 1.2 Current vs Target

| Aspect | Current | Target |
|--------|---------|--------|
| Agent flow | Agent → semantic search → documents | Agent → Skill → (optional) documents |
| Document structure | Monolithic (contract.md, api-definition.md) | Domain-split where beneficial |
| Token usage | Unpredictable (search + full file loads) | More predictable (skill + targeted loads) |
| Answer consistency | Varies by search results | Skill provides stable reference map |

---

## 2. Architecture

### 2.1 Flow Diagram

```
[Current]
  User question
       ↓
  Agent (semantic search)
       ↓
  contract.md, specs/, requirements/, code
       ↓
  Answer (quality depends on search)

[Target]
  User question
       ↓
  Agent reads matching Skill (triggered by description)
       ↓
  Skill: summary + document reference map
       ↓
  If needed: Agent loads only referenced docs/sections
       ↓
  Answer (consistent, token-efficient)
```

### 2.2 Principles

| Principle | Description |
|-----------|-------------|
| **Skill as router** | Skill tells Agent which documents to read for which question type. |
| **Documents = source of truth** | Contract, specs, requirements remain authoritative. Skills do not duplicate content. |
| **Progressive disclosure** | Skill has minimal summary; details live in documents. Load documents only when summary is insufficient. |
| **Domain alignment** | Skills and document chunks align by domain (auth-permission, search-history-decrypt, etc.). |
| **Requirement traceability** | When answering design/behavior Q&A, cite the requirement doc (path + §section). Do **not** load full doc; use skill's Document references. Do **not** invoke RequirementsPastSearch for Q&A. |

---

## 3. Skill Design

### 3.1 Skill Structure (per domain)

```markdown
---
name: <domain>-domain
description: <Trigger terms>. Use when user asks about <topics>.
---

# <Domain> Domain

## Quick reference
<3–5 line summary of key rules>

## Document references
| Question type | Document | Section |
|---------------|----------|---------|
| ... | ... | ... |

## Code references
| Concern | Location |
|---------|----------|
| ... | ... |
```

### 3.2 Skill Inventory (Priority Order)

| # | Skill | Trigger terms | Status |
|---|-------|---------------|--------|
| 1 | auth-permission-domain | 권한, 접근 제어, is_system_admin, permission group, 관리자, 화면 접근 | Done |
| 2 | search-history-decrypt-domain | 검색 이력, 복호화, 승인, 반려, 결재자, PENDING, DECRYPTION_NOT_APPROVED | Done |
| 3 | error-codes-domain | 에러 코드, FORBIDDEN, DECRYPTION_NOT_APPROVED | Done |
| 4 | department-approver-domain | 부서, 결재자 지정, department, decrypt_approver | Done |
| 5 | log-search-domain | 로그 검색, logType, pb_feplog, imagelog | Done |
| 6 | activity-statistics-domain | 활동 이력, 통계, scope, self, all | Done |
| 7 | ui-ux-domain | 메뉴, 화면, view, adminOnly, canAccessView | Done |

### 3.3 Skill Authoring Rules

- **Skill usage visibility**: When the agent uses a skill to answer, it must state at the start of the response: `[Skill used: <skill-name>]`. This lets the user see during the process that the skill was applied.
- **Description**: Include both WHAT and WHEN. Max 1024 chars. Third person.
- **Body**: Keep under 500 lines. Use progressive disclosure (link to reference.md if needed).
- **References**: One level deep. Point to file path + optional section/line range.
- **No duplication**: Do not copy contract/spec content into skill. Reference only.

---

## 4. Document Restructuring

### 4.1 Current Structure

```
docs/
├── contract.md          # All: env, API, DB, auth, permission
├── api-definition.md    # All APIs, error codes
├── specs/
│   ├── permission-group-hierarchy.spec.yaml
│   └── user-management.spec.yaml
└── requirements/        # Per-feature docs
```

### 4.2 Target Structure (Incremental)

**Phase 1–2**: No document split. Skills reference existing docs (contract.md, specs, api-definition.md) by section.

**Phase 3+**: Optional domain-split for token savings.

```
docs/
├── contract.md                    # Keep as index; may link to domain docs
├── contract/
│   ├── env-ports.md              # §환경·포트 (extracted)
│   ├── auth-permission.md        # §시스템 관리자, §화면 기반 접근 제어
│   ├── api-overview.md           # §API 규격
│   └── db-schema.md              # §DB 스키마
├── api-definition.md             # Keep; or split by domain later
├── specs/                        # Unchanged
└── requirements/                 # Unchanged
```

### 4.3 Chunking Criteria

Split a section into a separate file when:

- Section is > 500 lines or > ~1500 tokens.
- Section is referenced by a single domain skill frequently.
- Section changes independently (e.g., permission rules vs env config).

Do **not** split when:

- Section is small (< 200 lines) and stable.
- Splitting would create circular or fragile references.

### 4.4 Migration Strategy

1. **Phase 1–2**: Add skills that reference existing `contract.md` §sections, `specs/` §sections. No file moves.
2. **Phase 3**: Extract `docs/contract/auth-permission.md` from contract.md; update contract.md to link. Update auth-permission-domain skill to reference new path.
3. **Phase 4+**: Extract other domains as needed. Always update contract.md index and skill references.

---

## 5. Implementation Phases

### Phase 1: auth-permission-domain Skill (Weeks 1–2)

**Scope**: Single skill, no document changes.

| Task | Description | Status |
|------|-------------|--------|
| 1.1 | Create `.cursor/skills/auth-permission-domain/SKILL.md` | Done |
| 1.2 | Add Quick reference (is_system_admin vs permission group, admin-only rule) | Done |
| 1.3 | Add Document references table (contract.md §화면 기반 접근 제어, specs §4) | Done |
| 1.4 | Add Code references (ScreenAccessInterceptor, UserController, AuthService) | Done |
| 1.5 | Define test questions (5–10) for baseline vs post-skill comparison | Done (§7) |
| 1.6 | Run baseline: ask test questions without skill, record accuracy | Done |
| 1.7 | Enable skill, run same questions, record accuracy | Done |
| 1.8 | Compare Cursor Usage (token) before/after if visible | Pending |

**Deliverables**:

- [x] `auth-permission-domain/SKILL.md` — `.cursor/skills/auth-permission-domain/SKILL.md`
- [x] Test question list + baseline/post results (record in §7.3) — 2025-03-03 in-chat validation: 5/5 ✓ both baseline and post-skill

### Phase 2: search-history-decrypt + error-codes (Weeks 3–4)

**Scope**: Two more skills, no document split.

| Task | Description | Status |
|------|-------------|--------|
| 2.1 | Create `search-history-decrypt-domain/SKILL.md` | Done |
| 2.2 | Create `error-codes-domain/SKILL.md` | Done |
| 2.3 | Add document references to api-definition.md §6.1, §10, §11 | Done (skills reference existing) |
| 2.4 | Extend test question set; run baseline/post for new domains | Done |

### Phase 2.5: Requirement traceability in skills (token-efficient)

**Scope**: Enhance existing domain skills. No new subagent invocation for Q&A.

| Task | Description | Status |
|------|-------------|--------|
| 2.5.1 | Add Before answering #4 to auth-permission-domain: cite req doc (path + §) when explaining admin-only | Done |
| 2.5.2 | Add "처리 이력" / design rationale to Document references; principle: path+section only, no full doc load | Done |
| 2.5.3 | Add Requirement traceability principle to §2.2 | Done |

**Excluded**: Invoking RequirementsPastSearch for Q&A; loading full requirement docs for simple citation.

**Token optimization (skill growth)**: To prevent skill bloat, use **Core + TOPIC-INDEX**:
- **Core**: Skill lists only 2–3 foundational requirements per domain (design principles). Do **not** add every new requirement.
- **Full list**: `docs/requirements/TOPIC-INDEX.md` under the topic. Load only when user asks for "전체 처리 이력" or a doc not in core. TOPIC-INDEX is already maintained per new requirement.
- **Skill update**: Add to skill only when a requirement establishes a **new design principle**. Incremental/bugfix requirements go to TOPIC-INDEX only (no skill update).

### Phase 3: Remaining Skills + Optional Document Split (Weeks 5–8)

**Scope**: department-approver, log-search, activity-statistics, ui-ux skills. Optional extraction of `docs/contract/auth-permission.md`.

| Task | Description | Status |
|------|-------------|--------|
| 3.1 | Create remaining domain skills | Done (2025-03-03) |
| 3.2 | Decide: extract auth-permission from contract.md? If yes, create `docs/contract/auth-permission.md`, update contract.md, update skill | Deferred (no extraction; contract.md remains single file) |
| 3.3 | Review all skill descriptions for trigger overlap; refine | Done (2025-03-03) — overlap minimal; domain boundaries clear |
| 3.4 | Final test run; document results | Pending (test questions in §7.6; run when needed) |

### Phase 4: Maintenance & Iteration (Ongoing)

| Task | Frequency |
|------|-----------|
| Skill–document sync check | When contract/specs change |
| Test question re-run | Quarterly or after major permission/API changes |
| New domain skill | When new domain emerges (e.g., reporting, export) |

---

## 6. Success Metrics

### 6.1 Quality

| Metric | How to measure |
|--------|----------------|
| Answer accuracy | Test question set: % correct (manual check) |
| Key-rule inclusion | Did answer mention is_system_admin for admin-only? (Y/N) |
| Wrong-doc loading | Did Agent load irrelevant files? (qualitative) |

### 6.2 Efficiency

| Metric | How to measure |
|--------|----------------|
| Token usage | Cursor Settings → Usage (before/after skill, same period) |
| Turn count | Same question: turns to correct answer (fewer = better) |
| File load count | How many files loaded per question (manual or log if available) |

### 6.3 Maintenance

| Metric | How to measure |
|--------|----------------|
| Skill–doc drift | Quarterly: do skill references still match doc structure? |
| Update effort | Time to update skill when contract changes |

---

## 7. Test Question Set (Template)

Use for baseline and post-skill comparison.

### auth-permission-domain

| # | Question | Expected key points |
|---|----------|---------------------|
| 1 | user3에 관리자 권한 그룹을 줬는데 사용자 관리 접근이 안 돼요 | is_system_admin 필요, DB에서 설정 |
| 2 | 권한 그룹으로 admin-only API 접근이 되나요? | 안 됨, is_system_admin만 |
| 3 | scope=self와 scope=all 차이는? | self=본인, all=전체 |
| 4 | 화면 ID 목록이 어디에 정의되어 있나요? | specs §4.1, contract |
| 5 | 사용자 관리 API는 어디서 권한 체크하나요? | UserController, ScreenAccessInterceptor |

### search-history-decrypt-domain (Phase 2)

| # | Question | Expected key points |
|---|----------|---------------------|
| 1 | DECRYPTION_NOT_APPROVED가 뭔가요? | 승인 필요, searchHistoryId, 본인 소유·APPROVED·미만료 |
| 2 | ROW_NOT_IN_APPROVED_SNAPSHOT 의미? | 스냅샷에 포함된 row만 복호화 |
| 3 | 결재자와 관리자 차이? | decrypt_approver, is_system_admin, 둘 다 승인 가능 |

### error-codes-domain (Phase 2)

| # | Question | Expected key points |
|---|----------|---------------------|
| 1 | FORBIDDEN 에러 코드가 뭔가요? | 권한 없음, 403, admin-only |
| 2 | api-definition에 에러 코드는 어디 있나요? | §11 에러 코드 요약 |

### 7.3 Phase 1 Result Recording (auth-permission-domain)

**How to run**: New chat, ask each question. Mark ✓ (correct), △ (partial), ✗ (wrong).

| # | Question | Baseline (no skill) | Post-skill |
|---|----------|---------------------|------------|
| 1 | user3에 관리자 권한 그룹을 줬는데 사용자 관리 접근이 안 돼요 | ✓ | ✓ |
| 2 | 권한 그룹으로 admin-only API 접근이 되나요? | ✓ | ✓ |
| 3 | scope=self와 scope=all 차이는? | ✓ | ✓ |
| 4 | 화면 ID 목록이 어디에 정의되어 있나요? | ✓ | ✓ |
| 5 | 사용자 관리 API는 어디서 권한 체크하나요? | ✓ | ✓ |

**Usage (optional)**: Cursor Settings → Usage. Note daily token before/after skill enable for same period.

**Validation note (2025-03-03)**: In-chat validation performed. Baseline: codebase search only (no skill). Post-skill: auth-permission-domain skill used. Both runs: 5/5 ✓. Same session; token comparison (1.8) not performed.

### 7.4 Phase 1 Validation Guide (Tasks 1.6–1.8)

**1.6 Baseline (skill 비활성화)**  
1. Cursor Settings → Skills에서 `auth-permission-domain` 비활성화(또는 새 채팅에서 skill 미사용 확인).  
2. 새 채팅에서 §7 테스트 질문 1~5를 순서대로 질문.  
3. 각 답변을 §7.3 표에 Baseline 열에 ✓/△/✗로 기록.

**1.7 Post-skill (skill 활성화)**  
1. `auth-permission-domain` skill 활성화.  
2. 새 채팅에서 동일 질문 1~5를 순서대로 질문.  
3. §7.3 표 Post-skill 열에 ✓/△/✗로 기록.

**1.8 Token 비교 (선택)**  
- Cursor Settings → Usage에서 동일 기간 skill 비활성/활성 시 토큰 사용량 비교.

### 7.5 Phase 2 Result Recording (search-history-decrypt, error-codes)

| # | Question | Baseline | Post-skill |
|---|----------|----------|------------|
| 1 | DECRYPTION_NOT_APPROVED가 뭔가요? | ✓ | ✓ |
| 2 | ROW_NOT_IN_APPROVED_SNAPSHOT 의미? | ✓ | ✓ |
| 3 | 결재자와 관리자 차이? | ✓ | ✓ |
| 4 | FORBIDDEN 에러 코드가 뭔가요? | ✓ | ✓ |
| 5 | api-definition에 에러 코드는 어디 있나요? | ✓ | ✓ |

**Validation note (2025-03-03)**: In-chat validation. Both runs: 5/5 ✓.

### 7.6 Phase 3 Result Recording (department-approver, log-search, activity-statistics, ui-ux)

| # | Skill | Test question (example) | Baseline | Post-skill |
|---|-------|-------------------------|----------|------------|
| 1 | department-approver | decrypt_approver 테이블 구조는? | — | — |
| 2 | log-search | pb_feplog와 java_fw_imglog 차이? | — | — |
| 3 | activity-statistics | scope=self일 때 통계 API 동작? | — | — |
| 4 | ui-ux | canAccessView 로직은? | — | — |

**Note**: Run when validating Phase 3 skills. Record ✓/△/✗ per question.

---

## 8. Risk & Mitigation

| Risk | Mitigation |
|------|------------|
| Skill–doc drift | Document structure change checklist: update skill references |
| **Skill bloat** | **Core + TOPIC-INDEX**: Skill lists only 2–3 foundational reqs per domain. Full list in TOPIC-INDEX. Add to skill only when req establishes new design principle. |
| Over-triggering | Narrow skill descriptions; avoid generic terms |
| Token increase | Prefer skill-only answers; load docs only when needed |
| Maintenance burden | Start with 1 skill; expand only if value proven |

---

## 9. References

- Contract: `docs/contract.md`
- API definition: `docs/api-definition.md`
- Permission spec: `specs/permission-group-hierarchy.spec.yaml`
- Skill creation guide: `~/.cursor/skills-cursor/create-skill/SKILL.md`
- Existing skills: `.cursor/skills/db-domain/`, `dev-workflow/`, `requirement-doc/`, `test-workflow/`

---

## 10. Appendix: Implemented Skills

| Skill | Path | Phase |
|-------|------|-------|
| auth-permission-domain | `.cursor/skills/auth-permission-domain/SKILL.md` | 1 (2025-03-03) |
| search-history-decrypt-domain | `.cursor/skills/search-history-decrypt-domain/SKILL.md` | 2 (2025-03-03) |
| error-codes-domain | `.cursor/skills/error-codes-domain/SKILL.md` | 2 (2025-03-03) |
| department-approver-domain | `.cursor/skills/department-approver-domain/SKILL.md` | 3 (2025-03-03) |
| log-search-domain | `.cursor/skills/log-search-domain/SKILL.md` | 3 (2025-03-03) |
| activity-statistics-domain | `.cursor/skills/activity-statistics-domain/SKILL.md` | 3 (2025-03-03) |
| ui-ux-domain | `.cursor/skills/ui-ux-domain/SKILL.md` | 3 (2025-03-03) |

---

**Document end.**
