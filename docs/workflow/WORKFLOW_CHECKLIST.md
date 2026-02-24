# 워크플로우 체크리스트 (순서만)

**상세·예시·원칙**은 `DEVELOPMENT_WORKFLOW.md` 참고. 이 파일은 **순서와 게이트**만 정리한다.

## 게이트 (개발 전)

- 코드 수정(개발) 시작 **전에** 아래 1·2·3 완료 필수. 개발 끝난 뒤에 요건·테스트 계획 기록하지 않음.

## 순서

1. 요건 수집·분석 → 2. 요건 문서 작성 (§1·§2) → 3. 테스트 계획 수립 (§3 테스트 케이스 목록)
2. **(개인정보·복호화·접근통제 관련 시)** 보안 검토 — **Security** Subagent로 §2.1 보안 검토 또는 권고안 반영. 검토된 대로 설계·개발 진행. (참고: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §3)
3. (복잡 시) 스펙 작성 → 브랜치
4. 개발 (frontend/backend만, 기존 파일 복사·주석 후 신규)
5. 단위 테스트 (mvn test / npm test) · 통합(또는 curl) → §5 기록
6. 검증 · 재시작·정상 확인 (자동) · 실패 시 bugfix 반복
7. 문서화 (§5·오류 시 §6)
8. **해결 완료 시 Git commit** — 검증 통과·문서 반영 후 `.cursor/commands/commit-on-complete.md` 따라 커밋 (push는 사용자 요청 시에만)

## 참조

- 템플릿: `docs/template/REQUIREMENT_TEMPLATE.md`
- 상세: `docs/workflow/DEVELOPMENT_WORKFLOW.md`
- 보안 검토(개인정보·복호화·접근통제 시): **Security** Subagent, `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §3
- Cursor·문서·스크립트 연동 맵: `docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md`
- 해결 완료 후 커밋: `.cursor/commands/commit-on-complete.md`
