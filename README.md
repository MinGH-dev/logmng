# 로그 관리 (logmng) — 개발 워크스페이스

로그 검색, 활동 이력, 통계, 승인·사용자 관리 등을 제공하는 프론트엔드·백엔드 프로젝트입니다.  
이 폴더(`dev`)는 **개발 전용 워크스페이스**이며, Cursor 규칙·커맨드·서브에이전트가 여기서 동작합니다.

---

## 📂 구조

```
dev/
├── frontend/          # React (포트 3001)
├── backend/           # Spring Boot (포트 9200)
├── docs/              # 요건·계약·워크플로우·템플릿
│   ├── contract.md    # API·DB·포트 계약
│   ├── QUICK_START.md # 빠른 시작
│   ├── workflow/      # 워크플로우·서브에이전트 위임
│   ├── template/      # 요건·버그픽스 템플릿
│   ├── requirements/  # 요건 문서 (yyyyMMdd-이름.md)
│   └── design/        # UX 설계 표준
├── scripts/           # 서비스 기동/중지 (dev-services.sh)
└── .cursor/           # 규칙·커맨드·스킬·에이전트
```

---

## 🚀 빠른 시작

- **문서**: [docs/QUICK_START.md](docs/QUICK_START.md)
- **계약(API·포트)**: [docs/contract.md](docs/contract.md)  
  - 프론트엔드: http://localhost:3001  
  - 백엔드 API: http://localhost:9200/api  
  - DB: localhost:5432, DB `logmng`

```bash
# 서비스 재시작 (프로젝트 루트에서)
./scripts/dev-services.sh frontend restart   # 또는 backend | all
```

---

## 📋 Cursor로 작업할 때

- **새 요건/기능**: `/new-requirement` 후 요구사항을 적어 주세요. 요건 문서가 먼저 작성되고, 그다음 구현·검증·커밋 순으로 진행됩니다.
- **검증**: `/verify` — 재시작·헬스 체크·(프론트 변경 시) 브라우저 자동화 검증.
- **프롬프트 어떻게 할지 모르겠다면** → **[Cursor 프롬프팅 가이드](docs/CURSOR-PROMPTING-GUIDE.md)** 를 먼저 보세요. 자주 쓰는 요청 예시와 서브에이전트 위임 방법이 정리되어 있습니다.

### 주요 슬래시 커맨드

| 명령 | 설명 |
|------|------|
| `/new-requirement` | 새 요건 시작 — 요건 문서 작성 후 개발 |
| `/verify` | 검증(재시작·헬스·브라우저) 실행 |
| `/check-frontend-backend` | 프론트·백엔드 동작 확인 |

자세한 명령·스킬·서브에이전트: [docs/README.md](docs/README.md).

---

## 📚 문서

| 문서 | 설명 |
|------|------|
| [docs/README.md](docs/README.md) | 개발 문서 구조·요건 요청 방법·Cursor 명령 정리 |
| [docs/QUICK_START.md](docs/QUICK_START.md) | 개발 환경·실행·검증 |
| [docs/contract.md](docs/contract.md) | API·DB·포트 계약 |
| [docs/CURSOR-PROMPTING-GUIDE.md](docs/CURSOR-PROMPTING-GUIDE.md) | **Cursor 프롬프팅 가이드** — 요청 예시·서브에이전트 활용 |
| [docs/workflow/WORKFLOW_CHECKLIST.md](docs/workflow/WORKFLOW_CHECKLIST.md) | 워크플로우 순서·게이트 |
| [docs/workflow/SUBAGENT-DELEGATION.md](docs/workflow/SUBAGENT-DELEGATION.md) | 서브에이전트 위임 표 |

---

**마지막 업데이트**: 2026-02-26
