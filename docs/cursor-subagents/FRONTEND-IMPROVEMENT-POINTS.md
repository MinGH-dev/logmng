# 프론트엔드 Subagent 개선 포인트 (moai-adk 기준)

[moai-adk .claude/agents](https://github.com/modu-ai/moai-adk/tree/main/.claude)의 **expert-frontend**, **team-frontend-dev**를 참고해 이 프로젝트 프론트엔드 Subagent에서 반영할 수 있는 개선 포인트를 정리했다.

---

## 1. 스코프 경계 명시 (IN SCOPE / OUT OF SCOPE)

**moai**: expert-frontend에 IN SCOPE(컴포넌트 아키텍처, 상태관리, 성능, a11y, 라우팅, 테스트) / OUT OF SCOPE(백엔드 API, 비주얼 디자인·목업, DevOps, DB, 보안 감사)를 명시해 위임 시점이 분명함.

**이 프로젝트 적용**:
- Frontend 프롬프트에 **IN SCOPE**: `frontend/` 내 UI·컴포넌트·상태·스타일·API 호출(contract 기준). **OUT OF SCOPE**: 백엔드·DB·스펙 문서 직접 수정·배포 설정.
- 필요 시 “이건 Backend/Contract/Requirements에 맡겨라”라고 명시.

---

## 2. 접근성(A11y) · 품질 기준

**moai**: WCAG 2.1 AA, 시맨틱 HTML, ARIA, 키보드 내비게이션, 화면 리더 검증. team-frontend-dev는 “90%+ test coverage”, “Accessibility (WCAG 2.1 AA)”, “Responsive design”을 품질 기준으로 둠.

**이 프로젝트 적용**:
- 프론트 프롬프트에 **Accessibility**: 시맨틱 마크업, ARIA·키보드 접근 가능성, 가능하면 WCAG 2.1 AA 의식.
- **Testing**: Jest + React Testing Library로 단위/컴포넌트 테스트; 의미 있는 커버리지 목표(예: 신규/변경 컴포넌트 위주).
- **Responsive**: 뷰포트 대응 필요 시 명시.

---

## 3. 성능 의식

**moai**: Core Web Vitals(LCP, FID, CLS), 코드 스플리팅·lazy loading, React.memo/useMemo/useCallback, 가상 스크롤, 번들 분석.

**이 프로젝트 적용**:
- 프롬프트에 **Performance**: 큰 리스트는 가상화·페이지네이션 고려, 불필요한 리렌더 최소화, 동적 import 필요 시 코드 스플리팅 권장. (프로젝트가 React CRA 기준이면 Next 등 SSR은 OUT OF SCOPE로 두고, 클라이언트 번들·렌더링 위주로.)

---

## 4. 백엔드·계약과의 협업

**moai**: team-frontend-dev는 “Ask backend-dev about API response formats before implementing data fetching”, “Coordinate with backend-dev for API contracts and data shapes”.

**이 프로젝트 적용**:
- **API**: `docs/contract.md`, `specs/*.spec.yaml`에 정의된 API만 사용. 새 API·형식 변경이 필요하면 Contract/Backend에 “스펙 정의 필요” 안내.
- 응답 형식·에러 형식을 스펙/contract에 맞춰 호출·표시.

---

## 5. 출력 형식(아키텍처 작업 시)

**moai**: “Component hierarchy with props and state interfaces”, “State management architecture”, “Routing structure”, “Performance optimization plan”, “Testing strategy”, “Accessibility checklist”.

**이 프로젝트 적용**:
- 큰 기능·화면 추가 시: 컴포넌트 계층·props/state 요약, 상태 소유 위치, 해당 화면의 테스트·접근성 체크 항목을 짧게라도 정리하도록 프롬프트에 권장.

---

## 6. 모듈/기능별 프론트 Subagent (백엔드와 동일한 세분화)

**moai**: expert-frontend는 도메인·라이브러리 스킬(moai-domain-frontend, moai-library-shadcn 등)로 세분화. 이 프로젝트 백엔드는 Backend-Auth, Backend-ActivityLog, Backend-Log로 나눴음.

**이 프로젝트 적용**:
- **Frontend-Auth**: 로그인·인증 UI (LoginForm, 로그인 플로우, 인증 상태).
- **Frontend-ActivityLog**: 활동 통계·사용자 활동 로그 UI (ActivityStatistics, UserActivityLog/*, Statistics*).
- **Frontend-Log**: 로그 검색·테이블·이미지 로그·로그 타입 UI (LogGrid, LogTable, ImageLog*, SearchForm, AdvancedSearchForm, LogTypeSelector).
- **Frontend**: 공통(App, 라우팅, api, 공용 컴포넌트)·범위 불명확 시.

각 모듈 Subagent 프롬프트에 “이 영역만 수정, 다른 화면/서비스 건드리지 말 것”을 명시하면, 백엔드와 같이 수정 범위가 줄어든다.

---

## 7. 참고한 moai-adk 파일

- [expert-frontend.md](https://github.com/modu-ai/moai-adk/blob/main/.claude/agents/moai/expert-frontend.md): 스코프, 위임, 성능·a11y·테스트, 프레임워크 감지, Pencil MCP 등.
- [team-frontend-dev.md](https://github.com/modu-ai/moai-adk/blob/main/.claude/agents/moai/team-frontend-dev.md): 파일 소유권, 백엔드와 협업, 품질 기준(90%+ coverage, WCAG 2.1 AA, 반응형).

---

## 반영 현황

| 개선 포인트 | 반영 |
|-------------|------|
| 1. 스코프 경계 (IN/OUT) | ✅ `frontend.md`에 명시 |
| 2. 접근성·테스트 기준 | ✅ `frontend.md`에 요약 |
| 3. 성능 의식 | ✅ `frontend.md`에 요약 |
| 4. 백엔드·계약 협업 | ✅ 기존 contract 준수 + 협업 문구 보강 |
| 5. 출력 형식(아키텍처 시) | ✅ 설계/큰 변경 시 권장 문구 |
| 6. 모듈별 Frontend Subagent | ✅ Frontend-Auth, Frontend-ActivityLog, Frontend-Log 추가 |
