# Commit scope and untracked files (커밋 범위와 미추적 파일)

## Why some files stay untracked (누락 원인)

- **요건 완료 시 커밋**은 "해당 요건에 속한 파일만" 명시적으로 스테이징하는 방식을 권장합니다 (`commit-on-complete.md`).
- 그 결과 **요건과 직접 연결해 add하지 않은 파일**은 계속 Untracked로 남습니다.
- 특히 다음이 누락되기 쉽습니다.
  - **`.cursor/`**  
    agents, commands, rules, skills, subagents 등 — 특정 요건 커밋에 묶지 않고 추가한 경우.
  - **`docs/`**  
    workflow, requirements, template, cursor-subagents — 요건 문서는 해당 요건 커밋에 넣지만, 워크플로/서브에이전트 문서는 따로 add하지 않으면 누락.
  - **`specs/`**  
    새 스펙 파일을 만들었는데 해당 요건 커밋에 포함하지 않은 경우.

즉, **“이번 요건에서 수정/추가한 경로만” add**하다 보니, **새로 만든 .cursor/docs/specs 파일**이 커밋에 포함되지 않는 경우가 반복됩니다.

## How to prevent (재발 방지)

1. **규칙**
   - **.cursor, docs, specs** 에서 새로 만들거나 수정한 파일은  
     - 해당 요건 커밋에 함께 넣거나,  
     - 별도 커밋(예: `chore: update .cursor agents` / `docs: add workflow X`)으로 반드시 커밋합니다.
   - push 전에 아래 체크 스크립트로 한 번 확인하면 좋습니다.

2. **체크 스크립트**
   - `./scripts/check-untracked-docs.sh`  
     `.cursor/`, `docs/`, `specs/` 아래 Untracked 파일이 있으면 목록을 출력합니다.  
     여기 나온 파일 중 “저장소에 올려야 할 것”이 있으면 add 후 커밋합니다.

3. **일괄 추가가 필요한 경우**
   - 프로젝트 설정/문서 동기화용으로 한 번에 올리고 싶을 때:
     ```bash
     git add .cursor docs specs
     git status   # 확인 후
     git commit -m "chore: sync .cursor, docs, specs"
     ```

## Reference

- `commit-on-complete.md`: 요건 단위 커밋 시 스테이징 범위
- 이 문서: .cursor/docs/specs 누락 원인 및 방지
