# Analysis: 통계 검색 필드 사용자 필드 크기가 활동 이력과 동일하게 적용되지 않은 원인

**Date**: 2026-03-13  
**Trigger**: 사용자 피드백 — "검색 필드에서 사용자의 필드들이 활동 이력의 사용자 필드들과 동일한 크기로 개선이 안됐다."

---

## 1. 결론 요약

| 구분 | 누락 여부 | 설명 |
|------|-----------|------|
| **요구사항 문서** | **부분 누락** | §1 Expected outcome에 "통계의 사용자 필드(부서·사용자명·사용자 ID)는 활동 이력과 **동일한 필드 크기(width)**로 표시한다"는 **명시적 한 줄이 없음**. 디자인 문서(Width by role, §4 동일 크기) 참조와 Implementation note로만 **간접** 전달됨. |
| **프론트엔드 구현** | **누락 있음** | (1) 통계 row-1 레이아웃에서 사용자 블록이 **로그 타입과 한 셀(1fr)**을 공유해, 사용자 블록에 할당되는 공간이 활동 이력보다 좁음. (2) UserContextFilterBlock이 **표준 블록 너비**(--sf-field-user-block-max) 또는 동일 필드 min/max를 두 화면에서 공통 적용하지 않아, 시각적 크기가 다르게 나옴. |

---

## 2. 디자인 문서 (명확히 정의됨)

- **docs/design/search-fields-by-screen.md**
  - §3 통계: "department, username, userId | ... | **활동 이력과 동일** | 34px | 6px 8–10px"
  - §4: "사용자 맥락 화면(활동 이력, 통계 등): 부서·사용자명·사용자 ID는 … **동일 축·동일 크기 유지**."
  - §4: "필드 너비 — 최대 글자 수 기준, 모든 화면 동일. **기준은 사용자 활동 이력 화면**. … **어느 화면에서나 동일**하게 적용."
- **docs/design/search-field-definition-items.md**
  - §4: "For a field that appears on both activity-log and statistics (e.g. 부서, 사용자명, 사용자 ID, IP), **width**, height, padding … must be **identical**."
  - §4.5: 사용자명/사용자 ID/부서 — min 100px, 1fr 등; "**화면 간 동일 적용**", "어느 화면에서나 동일하게 적용".

→ 디자인 문서에는 **사용자 필드 크기를 활동 이력·통계 간 동일하게** 하라는 내용이 명확함.

---

## 3. 요구사항 문서 (명시 부족)

- **docs/requirements/20260313-activity-log-statistics-design-standards.md**
  - §1 Expected outcome: "same panel width", "same compact spacing", "Activity log layout", "Single row for non-date", "Form per mode" 등은 있음.
  - **없는 것**: "User block fields (부서, 사용자명, 사용자 ID) are the **same width/size** on activity log and statistics" 또는 "통계의 사용자 필드는 활동 이력과 동일한 필드 크기로 표시한다"는 **한 줄**.
  - §2 UserContextFilterBlock: "ensure compact spacing uses standard or same values as **both screens**" — **spacing**만 언급, **field width** 동일은 문장 없음.
  - §2 Implementation note: "read and apply field-level and **layout values**" from design docs — 디자인 문서를 읽으면 "동일 크기" 규칙을 알 수 있으나, 요구사항 본문에 기대 결과로 적혀 있지 않음.
  - §2 Planned change file list: UserContextFilterBlock.css에 "Compact spacing aligned with standard"만 있고, "**user block field width 동일 적용 (활동 이력·통계)**" 같은 작업 항목 없음.

→ 요구사항에서는 **간접 참조**만 있고, **기대 결과·작업 목록**에 사용자 필드 크기 동일이 명시되지 않아, 구현·QA 시 우선순위에서 밀릴 수 있음.

---

## 4. 프론트엔드 구현 (구조적 원인)

- **UserContextFilterBlock.css** (활동 이력·통계 공통)
  - `.user-context-filter-block--compact .user-context-filter-block__row`: `grid-template-columns: repeat(3, minmax(100px, 1fr))` (769px 이상에서 120px).
  - 필드: `min-width: 4.5em; max-width: none; width: 100%` — 그리드 셀 안에서만 확장.
  - **표준 변수**: `--sf-field-user-block-min/max`는 **사용하지 않음**. 블록 전체 너비를 고정하지 않음.

- **StatisticsFilters.css**
  - `.statistics-filters__row-1`: `grid-template-columns: minmax(var(--sf-field-date-min), 180px) 1fr`.
  - 두 번째 컬럼 `1fr` 안에 **로그 타입 select**와 **UserContextFilterBlock**이 함께 들어감.
  - 따라서 사용자 블록이 받는 **가용 너비**가 활동 이력보다 작을 수 있음 (활동 이력 row-2에서는 사용자 블록이 flex 아이템으로 더 넓은 공간 사용).

- **UserActivityLog.css**
  - `.search-form-row-2 .user-context-filter-block--compact`: `flex: 0 1 auto; margin-right: var(--sf-block-gap)` — row-2 flex 레이아웃에서 사용자 블록이 기타 조건·버튼과 나란히 배치되어, 상대적으로 넓은 영역을 가짐.

→ **통계**에서는 사용자 블록이 (1) 로그 타입과 한 셀을 나눠 쓰고, (2) 블록 단위 max-width가 없어, **활동 이력과 동일한 필드 크기**가 보장되지 않음.

---

## 5. 권장 조치

1. **요구사항 문서 보강**
   - §1 Expected outcome에 한 줄 추가:  
     "**User block field size (동일 크기)**: 부서, 사용자명, 사용자 ID 필드는 활동 이력과 통계에서 **동일한 min/max width 및 시각적 크기**로 표시한다. Per `docs/design/search-fields-by-screen.md` §3 (활동 이력과 동일), §4 (화면 간 동일 적용) and `docs/design/search-field-definition-items.md` §4, §4.5."
   - §2 Planned change file list에 UserContextFilterBlock.css / StatisticsFilters 관련: "Apply same user block field width on both screens (or use --sf-field-user-block-* so block width is consistent); ensure statistics row layout gives user block sufficient width so field sizes match activity log."

2. **프론트엔드 수정**
   - 통계 row-1에서 사용자 블록이 **로그 타입과 동일 셀**을 쓰지 않도록 레이아웃 조정 (예: 로그 타입 | 사용자 블록 | … 별도 컬럼, 또는 사용자 블록에 min-width 적용).
   - UserContextFilterBlock 또는 두 화면 공통 래퍼에서 **블록 단위 max-width** (`var(--sf-field-user-block-max)`) 적용 검토하여, 두 화면에서 사용자 블록이 비슷한 너비를 갖도록 함.
   - 디자인 문서 §4.5에 맞춰 사용자 블록 필드에 **동일 min-width**(예: 100px) 적용 여부 확인 (이미 minmax(100px, 1fr)이면, 통계 측 컨테이너가 좁아서 100px만 쓰이지 않도록 공간 할당 조정).

3. **테스트 케이스**
   - §3에 TC 추가 검토: "Compare user block fields (부서, 사용자명, 사용자 ID) on activity log and statistics — same min/max width and visual size."

---

---

## 6. 확인 결과 (요청: "요구사항/프론트엔드 에이전트에서 누락한건지 확인")

| 구분 | 누락 여부 | 상세 |
|------|-----------|------|
| **요구사항 문서** | **부분 누락** | REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4에는 "Field width by role (user block)"·§1 Expected outcome에 사용자 블록 동일 크기 명시·§2 change list에 적용 항목이 **필수**로 되어 있으나, 20260313 초안에는 §1에 "user block same width/size" 한 줄이 없었고, §2 UserContextFilterBlock·change list에는 spacing만 있고 **field width 동일 적용** 문구가 없었음. |
| **프론트엔드 구현** | **누락 있음** | (1) StatisticsFilters row-1이 `1fr` 한 셀에 로그 타입 + UserContextFilterBlock을 함께 넣어 사용자 블록 가용 너비가 활동 이력보다 좁음. (2) UserContextFilterBlock.css는 `minmax(100px, 1fr)` 등만 사용하고 `var(--sf-field-user-block-min/max)` 미사용. |
| **조치** | 완료 | 요구사항 문서 20260313에 §1 Expected outcome "User block field size (동일 크기)" 항목 추가, §2 Solution·Planned change list에 사용자 블록 필드 너비 동일 적용 및 통계 row-1 레이아웃 보강 명시, §3에 TC-10 및 Scenario 5 추가. Frontend 수정은 별도 구현(핸드오프) 필요. |

---

## 7. 도구 개선 (다른 화면 개선 요청 시 누락 방지)

사용자 요청: "다음에도 다른 화면에 대한 개선을 요청할 시, 누락이 없도록 도구를 개선해줘."

반영한 도구 변경:

| 도구 | 파일 | 변경 내용 |
|------|------|-----------|
| **Change target checklist** | `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` | §2.4 적용 시 **필수 검증 테이블** 추가: §1에 사용자 블록 동일 크기 **명시적** 불릿, §2/change list에 동일 필드 너비 적용·레이아웃으로 눌리지 않게, §3에 사용자 블록 필드 크기 비교 TC. §2 실행 시 "§2.4 verification 테이블 실행" 단계 명시. |
| **Authoring workflow** | `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` | step 5.5에서 §2.4 적용 시 **§2.4 verification** 테이블 실행 (세 가지 확인) 필수로 명시. |
| **Handoff checklist** | `docs/workflow/HANDOFF-CHECKLIST.md` | Frontend 핸드오프에 **"User block / field width (when §2.4)"** 체크 항목 추가: 두 화면 이상 정렬 시 사용자 블록 동일 너비 적용·한 셀 공유 금지·TC 검증 문구 포함. |
| **Frontend subagent** | `docs/cursor-subagents/frontend.md` | **"User block field size (when aligning screens)"** 문단 추가: 화면 정렬 시 부서·사용자명·사용자 ID 동일 min/max·시각적 크기, 1fr 단일 셀 공유 금지, TC 확인. |
| **Rule** | `.cursor/rules/search-filter-form-design.mdc` | Width by role에 **Shared blocks**에 블록 단위 변수 사용, **Aligning multiple screens**에 사용자 블록 동일 크기·한 셀 공유 금지 명시. |
| **Requirement-doc skill** | `.cursor/skills/requirement-doc/SKILL.md` | §2.4 시 §1 **명시적** 불릿, §2/change list 동일 너비·레이아웃, §3 TC, 및 **§2.4 verification 테이블** 실행 필수 문구 추가. |

**Author**: Main agent (analysis)  
**Date**: 2026-03-13
