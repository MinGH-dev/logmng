# LogMng 오프라인 번들 (폐쇄망 설치)

## 이 tarball에 포함된 것

| 경로 | 내용 |
|------|------|
| `install-offline.sh` | 설치·기동 통합 스크립트 |
| `README-OFFLINE.md` | 본 문서 |
| `BUNDLE-VERSION.txt` | 빌드 시각·버전·(가능 시) git 커밋 |
| `MANIFEST.txt` | 번들 내 모든 파일 목록 |
| `bin/backend/` | Spring Boot fat JAR(의존 라이브러리 내장), `run.sh` |
| `bin/frontend/` | 정적 UI `www/`, JDK 정적 서버 JAR, `run.sh` |
| `db/` | PostgreSQL DDL·마이그레이션·시드·`setup.sh`·`check-db.sh` 등 전부 |
| `docs/` | `contract.md`, `DB_SETUP_GUIDE.md`, `BIN-DEPLOY-README.md` |

**포함하지 않는 것**: JVM(JDK/JRE), PostgreSQL 서버 본체 — OS/인프라에 별도 설치.

## 전제

- **이 tarball/디렉터리 안에는** 애플리케이션 JAR·정적 UI·DB SQL·설치 스크립트만 포함됩니다. **인터넷이 필요 없습니다.**
- **번들 제작**은 인터넷·Maven·npm이 있는 빌드 PC에서 **한 번** `scripts/build-offline-bundle.sh` 로 수행합니다 (저장소 루트).
- **설치 서버** 사전 요구: **JDK/JRE 17+** (`java` 명령), **PostgreSQL 서버** 가동, **`psql` 클라이언트**, `bash`.

## 설치 서버에서 (오프라인)

```bash
tar xzf logmng-offline-1.0.0.tar.gz
cd logmng-offline-1.0.0
chmod +x install-offline.sh
./install-offline.sh all
```

`all` 한 번이면(대화형): 사전 점검 → DB DDL 적용(선택) → 설정 파일 작성 → 백엔드·프론트 기동(nohup)까지 진행합니다.

### 개별 명령

| 명령 | 설명 |
|------|------|
| `./install-offline.sh check` | `java`, `psql`, 필수 파일 존재 확인 |
| `./install-offline.sh db` | DB 연결 정보 입력 후 번들 내 `db/setup.sh` 실행 |
| `./install-offline.sh configure` | `var/logmng.env` 생성(비밀번호·CORS·포트 등) |
| `./install-offline.sh start` | 환경 로드 후 백엔드·UI 기동 |
| `./install-offline.sh stop` | pid 기준 종료 |
| `./install-offline.sh status` | 프로세스·포트 안내 |
| `./install-offline.sh all` | 위 순서 통합 마법사 |

로그: `var/log/backend.log`, `var/log/frontend.log`  
PID: `var/run/backend.pid`, `var/run/frontend.pid`  
설정: `var/logmng.env` (권한 600, 비밀 포함)

## API URL과 프론트

정적 UI의 `REACT_APP_API_BASE_URL`은 **번들을 만들 때** 빌드 PC에서 박힙니다. 배포 후 API 주소가 다르면 **빌드 PC에서 `REACT_APP_API_BASE_URL=... ./scripts/build-offline-bundle.sh` 로 번들을 다시 만든 뒤** 다시 배포하세요.

## CORS

UI를 `http://서버:3001` 로 열면 백엔드에 `CORS_ALLOWED_ORIGINS`에 동일 Origin을 넣어야 합니다. `configure` 단계에서 묻습니다.
