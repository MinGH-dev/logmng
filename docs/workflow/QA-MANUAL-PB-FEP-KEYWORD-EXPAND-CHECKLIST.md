# Manual QA checklist — PB FEP v2.0.0 keyword highlight (expand all)

---

## 한국어 — 수동 QA 절차 (요약)

**전제 (Docker 스택 `:3001`)**: `frontend/` 또는 `backend/` 소스를 바꾼 뒤 **Docker Compose**로 UI를 검증할 때는, 수동 브라우저 QA **이전에** 저장소 루트에서 **`./scripts/docker-dev-sync.sh`**를 실행해 `dist/`와 이미지가 작업 트리와 맞는지 맞춘다(동등 절차·상세: [`docs/workflow/DOCKER-LOCAL-AGENTS.md`](DOCKER-LOCAL-AGENTS.md) §11–12). 워크스페이스에서 **`npm test`** / **`mvn test`** 등은 CI 스타일 점검용으로 그대로 두고, **3001에서 브라우저로 확인**할 때만 소스 변경 후 동기화가 필요하다.

1. 브라우저에서 **`http://localhost:3001`** 접속 → 로그인.
2. **PB FEP v2.0.0** 화면으로 이동 (pb-fep-log-search).
3. 검색어 입력 (예: `LOCAL-PB`) 후 검색 실행.
4. 결과 표시 후 **전체 펼치기** 클릭.
5. 개발자 도구 **콘솔**에서 아래 실행 후 개수 확인:

```javascript
document.querySelectorAll('.log-table mark').length
document.querySelectorAll('.pb-fep-stream-panel .stream-line mark').length
document.querySelectorAll('.stream-line.stream-line--keyword-hit').length
```

6. **기대값**: 검색어가 있고 펼친 행의 스트림에 키워드가 매칭되면(시나리오 A) `mark` 또는 `stream-line--keyword-hit`이 1개 이상인 경우가 일반적. 검색어 없음(B)·페이로드에 문자열 매칭 없음(C)·복호화만 매칭(D)은 아래 표 참고.

---

**Purpose**: Human verification of keyword highlighting after **전체 펼치기** on PB FEP log search (`pb-fep-log-search`), using the Docker frontend URL per contract.

**References (project)**:
- Verification order and health checks: `.cursor/commands/verify.md`
- Integration checklist themes: `docs/workflow/DEVELOPMENT_WORKFLOW.md` §5 Verification checklist
- Requirement test structure (§3 / §5): `docs/template/REQUIREMENT_TEMPLATE.md`
- Docker local stack, sync after source changes: [`docs/workflow/DOCKER-LOCAL-AGENTS.md`](DOCKER-LOCAL-AGENTS.md) §11–12; script: [`scripts/docker-dev-sync.sh`](../../scripts/docker-dev-sync.sh) (repo root)
- UI implementation: `frontend/src/components/LogTable.js`, `frontend/src/components/LogTable.css`

**Environment**
- **Base URL**: `http://localhost:3001` (canonical frontend per contract; Docker Compose static frontend / dist bundle)
- **Precondition (Docker UI QA after `frontend/` or `backend/` changes)**: Before manual browser QA on this URL, run **`./scripts/docker-dev-sync.sh`** from the repository root (or equivalent per [`docs/workflow/DOCKER-LOCAL-AGENTS.md`](DOCKER-LOCAL-AGENTS.md)) so **`dist/` and container images match the working tree**. Host **`npm test`** (frontend) and **`mvn test`** (backend) remain the workspace checks for automated/CI-style validation; **browser verification on `:3001` with Docker** requires this sync step when sources changed.
- Other preconditions: backend reachable if login/search requires API (e.g. health on 9200 per verify.md)

---

## Steps (exact)

1. Open **`http://localhost:3001`** in a supported browser.
2. **Log in** with a valid test account (same as usual QA).
3. Navigate to **PB FEP v2.0.0** (screen tied to **pb-fep-log-search** / wireframe SVG layout).
4. In the search form, enter **keywords** that are known to appear in stream payload for your dataset (example: `LOCAL-PB` if seeded data contains it). Run **search**.
5. Wait until result rows are shown (no loading spinner on the grid).
6. Click **전체 펼치기** (expand all rows / stream panels).
7. Open **Developer Tools** → **Console**.
8. Run the following (primary selectors match `LogTable.js` / CSS: `.log-table`, `.pb-fep-stream-panel`, `.stream-line`, `mark`):

```javascript
// Table-wide marks (ImageLog parity styling: .log-table mark)
document.querySelectorAll('.log-table mark').length

// Optional — marks only inside PB FEP stream panel
document.querySelectorAll('.pb-fep-stream-panel .stream-line mark').length

// Optional — full-line keyword-hit emphasis (class on stream lines)
document.querySelectorAll('.stream-line.stream-line--keyword-hit').length
```

9. (Optional visual) Confirm highlighted lines or `<mark>` in **Elements** panel under expanded row → `.pb-fep-stream-panel` → `.stream-lines` → `.stream-line`.

---

## Expected results

| Scenario | Keywords | Match in visible stream text? | Expect |
|----------|----------|-------------------------------|--------|
| A — Happy path | Non-empty | At least one line contains a keyword substring after decrypt/display | **≥ 1** `.log-table mark` **or** **≥ 1** `.stream-line--keyword-hit` on expanded rows (often both). |
| B — Empty keywords | Cleared / not sent | N/A | **0** marks from keyword HTML; lines are plain text. **No** requirement for `stream-line--keyword-hit` from client keyword logic. |
| C — No match in payload | Non-empty | No substring match in stream lines | Typically **0** `mark`; **0** `stream-line--keyword-hit` **unless** server indicates decrypt-only bulk match (see D). |
| D — Decrypt-only match (server flags) | Non-empty | No literal keyword in plaintext lines, but API sets PB FEP `keyword_match_*` | May see **≥ 1** `.stream-line--keyword-hit` on **all** stream lines **without** `<mark>` (bulk line tint). BMSG column may use `td.pb-fep-bmsg--keyword-hit` instead of inline marks — check Elements if console counts are 0. |

**Note**: Class names are defined in `LogTable.js` (`stream-line`, `stream-line--keyword-hit`, `pb-fep-stream-panel`) and styles under `.log-table mark` in `LogTable.css`. If selectors return **0** in scenario A, record row keys, keyword, and a snippet of stream text for the bug report.

---

## Record (paste into requirement §5 or QA log)

- Date:
- Browser / OS:
- Keyword(s) used:
- Scenario (A / B / C / D):
- `document.querySelectorAll('.log-table mark').length` = 
- `document.querySelectorAll('.stream-line.stream-line--keyword-hit').length` = 
- Pass / Fail + notes:
