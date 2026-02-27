# Release Checklist

릴리스 시 수행할 단계. Release 서브에이전트는 이 문서를 유지·갱신하며, **실행은 담당하지 않음**(개발자 또는 사용자가 체크리스트대로 수행).

## 순서

1. **요건 해결 확인**  
   - 검증 통과, §5·§6 반영, 로컬 commit 완료 (`commit-on-complete.md`).

2. **CHANGELOG·버전**  
   - CHANGELOG에 해당 릴리스 항목 추가.  
   - 필요 시 버전 부여(예: package.json / pom.xml X.Y.Z). Release 에이전트가 안내만 함.

3. **태그**  
   - 버전 태그 생성(예: `git tag vX.Y.Z`). 선택 사항.

4. **빌드·테스트**  
   - 백엔드: `cd backend && mvn clean package` (또는 test 포함).  
   - 프론트엔드: `cd frontend && npm run build` / `npm test -- --watchAll=false`.  
   - 통합/수동 검증 필요 시 수행.

5. **원격 push**  
   - 현재 브랜치를 원격에 반영: `git push origin <branch>` (예: `git push origin feat/xxx`).  
   - 태그 푸시 시: `git push origin vX.Y.Z` (태그 사용 시).

6. **배포**  
   - 환경에 맞는 배포 절차(배포 스크립트, CI/CD, 수동 배포 등) 수행.  
   - 배포 상세는 Documentation/운영 문서 참고.

## 수행 기록 (참고)

- **2026-02-27**: docs/workflow, .cursor 업데이트 (DB-AGENT-REVIEW, QA-BROWSER-TEST-TROUBLESHOOTING, SUBAGENT-MODEL-SELECTION, agent delegation). CHANGELOG 2026-02-27 항목 추가, 릴리스 커밋 후 `git push` 수행. (1–2, 5 수행)
- **2026-02-27**: SUBAGENT-MODEL-SELECTION concrete model names (claude-haiku-4.5, sonnet4.6), agent-collaboration/SUBAGENT-DELEGATION alignment. CHANGELOG 2026-02-27 항목 추가, 릴리스 커밋 후 `git push` 수행. (1–2, 5 수행)
- **2026-02-27**: User permission hierarchy + permission group management (req 20250227-user-permission-hierarchy-group, bugfix-1) 반영. CHANGELOG 2026-02-27 항목 추가, 릴리스 커밋 후 `git push` 수행. (1–2, 5 수행)
- **2026-02-27**: Release 작업 — 현재 변경(agent-collaboration, SUBAGENT-MODEL-SELECTION)에 대해 CHANGELOG·릴리스 체크리스트 갱신, 커밋 후 `git push` 수행. (1–2, 5 수행)
- **2026-02-25**: design standards·UX agent 커밋 후 `feat/cursor-commit-on-complete` 푸시 완료. CHANGELOG 2026-02-25 항목 반영. (1–2, 5 수행)

## 참고

- 로컬만 commit하고 push 안 하는 일반 개발 흐름: `.cursor/commands/commit-on-complete.md` (push는 사용자 요청 시에만).
- **릴리스**로 마무리할 때는 위 체크리스트에 따라 **원격 push까지** 수행하는 것을 권장.
