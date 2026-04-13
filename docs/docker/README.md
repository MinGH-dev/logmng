# Docker로 로컬 실행 (dist 오프라인 번들 · PostgreSQL 16 · 3 DB)

이 문서는 **소스 트리에서 임시 빌드하지 않고**, 릴리스와 동일한 **`dist/logmng-offline-<VERSION>/`** 산출물을 기준으로 `docker compose`로 스택을 띄우는 절차를 설명합니다.  
Compose·Dockerfile은 **`docker/`** 에 있고, 환경 예시 **`.env.docker.example`** 은 **저장소 루트**에 있습니다(복사본 `.env.docker` 동일).

### 수동 테스트 한 번에 (권장)

LDAP·브라우저 E2E 자동화 없이 **직접 브라우저로 확인**할 때:

```bash
./scripts/docker-local-manual-test.sh up
```

최초 실행 시 `.env.docker`가 없으면 `.env.docker.example`이 **자동 복사**되고 같은 실행에서 계속 진행합니다(로컬 플레이스홀더). 비밀·키는 필요 시 수정하세요. **에이전트/CI 검증 단계**는 [`docker/README.md`](../../docker/README.md) 의 **Agent / CI checklist** 를 따릅니다. 상세·트러블슈팅: 같은 문서의 *One-shot*·*Prerequisites* 절.

## 전제 (dist 레이아웃 단일 진실)

| 산출물 | 역할 |
|--------|------|
| **`dist/logmng-offline-<VERSION>/`** | Docker 이미지가 `COPY`하거나 마운트하는 **정본 트리** — `bin/backend`, `bin/frontend`, `db/` 등 |
| **`dist/logmng-offline-<VERSION>.tar.gz`** | 위 디렉터리와 동일 내용의 tarball (풀어서 사용 가능) |
| **`./scripts/package-airgap-bin.sh`만 실행** | 루트 `bin/`만 채움 — Docker용 **전체 오프라인 트리**(`db/` 포함)는 **`build-offline-bundle.sh`** 로 만듭니다 |

자세한 번들 구조: [`bin/README.md`](../../bin/README.md), [`scripts/offline-bundle/README-OFFLINE.md`](../../scripts/offline-bundle/README-OFFLINE.md).

## 1. `dist/` 채우기

저장소 **루트**에서 인터넷이 있는 빌드 머신으로 실행합니다.

```bash
./scripts/build-offline-bundle.sh
```

- **번들 버전**: 스크립트는 환경 변수 **`VERSION`**(기본 `1.0.1`)으로 출력 디렉터리명을 정합니다 — 결과는 **`dist/logmng-offline-<VERSION>/`** 입니다.
- **tar 없이 디렉터리만** (개발 편의): `VERSION=1.0.1 NO_TAR=1 ./scripts/build-offline-bundle.sh`
- 생성 확인(TC-01): `dist/logmng-offline-<VERSION>/` 아래에 `bin/backend/*.jar`, `bin/frontend/www/`, `db/`, `MANIFEST.txt`, `BUNDLE-VERSION.txt` 등이 있어야 합니다.

Compose나 빌드 인자에서 **동일 버전**을 가리키는 이름(예: `DIST_VERSION`)이 있다면, **`VERSION`과 같은 값**으로 맞춥니다.

## 2. 환경 변수 (예시 파일)

- **루트**의 **`.env.docker.example`** 을 복사해 `.env.docker` 를 만들고 로컬 전용 값을 넣습니다. **예시만 커밋**하고, 비밀번호·`ENCRYPTION_KEY` 등은 커밋하지 않습니다.
- Spring Boot·다중 데이터소스에 필요한 키는 [`docs/contract.md`](../contract.md)와 `backend/src/main/resources/application.yml`과 일치해야 합니다. 예:
  - `SPRING_DATASOURCE_*` (Primary → DB **`logmng`**)
  - `APP_DATASOURCE_PB_*` (PB FEP → **`pbfep`**)
  - `APP_DATASOURCE_IMAGELOG_*` (ImageLog → **`imagelog`**)
  - `APP_DB_SCHEMA_SYS` / `APP_DB_SCHEMA_PB` / `APP_DB_SCHEMA_IMAGELOG`
  - `CORS_ALLOWED_ORIGINS`, `ENCRYPTION_KEY` 등 비대화형 기동에 필요한 항목

PostgreSQL 16 위에 **물리 DB 세 개** `logmng`, `pbfep`, `imagelog`가 있어야 하며, 초기화는 `dist/.../db/setup.sh` 및 [`backend/DB_SETUP_GUIDE.md`](../../backend/DB_SETUP_GUIDE.md) 절차와 맞춥니다(구현에 따라 init 컨테이너 또는 `docker compose run`으로 `setup.sh` 호출).

## 3. Compose 기동

저장소 **루트**에서 compose 파일을 지정합니다. 변수 치환(`POSTGRES_PUBLISH_PORT`, `OFFLINE_ROOT` 등)을 맞추려면 **루트의 `.env.docker`** 를 사용하세요.

```bash
# docker compose V2 플러그인이 있을 때
docker compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . up -d

# 또는 standalone docker-compose (플러그인 없이 자주 쓰임)
docker-compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . up -d
```

한 번에 기동·번들 빌드는 `./scripts/docker-local-manual-test.sh up` 권장(`.env.docker` 로드 및 `DIST_VERSION`/`OFFLINE_ROOT` 정렬).

- 백엔드·프론트·DB **게시 포트**: 계약상 백엔드 **9200**, 정적 UI **3001**([`docs/contract.md`](../contract.md)). Postgres **호스트** 포트는 기본 **5433**(`POSTGRES_PUBLISH_PORT`)이며, 컨테이너 내부는 5432입니다.

## 4. 헬스·스모크 (QA TC-04 ~ TC-06)

호스트에서 포트가 위와 같이 매핑되었다고 가정할 때:

| TC | 확인 | 예시 명령 |
|----|------|-----------|
| **TC-04** | 백엔드 헬스 | `curl -s http://localhost:9200/api/health` → HTTP 200 및 healthy JSON |
| **TC-05** | DB 연결 | `curl -s http://localhost:9200/api/db/test` → `data.connected === true` 등 계약과 동일 |
| **TC-06** | 프론트 정적 서버 | `curl -s -o /dev/null -w "%{http_code}" http://localhost:3001/` → 2xx (또는 문서화된 경로) |

브라우저 스모크(TC-07)는 요건에 따라 선택입니다(문서 하단 **인터뷰 · 미결정 사항** 표 참고).

## 5. `mvn test` 테스트 이미지 (TC-08)

요구사항: **Linux 9.6 계열**, **JDK 17**, DB는 **PostgreSQL 16** — `backend/`에서 **`mvn test`**(또는 문서화된 동일 목적 명령)를 **테스트 전용 Docker 이미지**나 CI 단계에서 실행합니다.

1. **`docker/`** 에서 테스트 러너용 `Dockerfile`(예: `Dockerfile.mvn-test`, `test/Dockerfile` 등)을 찾아 이미지를 빌드합니다.
2. PostgreSQL 16이 compose 네트워크에 있으면, 문서화된 대로 **`--network`** 또는 compose 서비스 이름으로 접속합니다(Testcontainers 등 사용 시 그 절차를 따름).
3. 종료 코드 0 — 테스트 통과.

정확한 `docker build` / `docker run` 인자는 **`docker/`** 내 주석 및 예시를 따릅니다.

## 6. 보안·CORS

- 비밀은 **환경·Docker secrets**로만 주입합니다. [`docs/security-guide.md`](../security-guide.md) 참고.
- UI와 API의 호스트/포트가 다르면 브라우저 요청을 위해 **`CORS_ALLOWED_ORIGINS`** 에 UI 출처를 포함합니다.

## 인터뷰 · 미결정 사항 (제품 / 운영 결정)

아래는 요구사항 문서에 따라 **자동으로 범위를 넓히지 않으며**, 별도 결정 전까지 선택 사항으로 둡니다.

| 주제 | 질문 |
|------|------|
| Browser E2E | Playwright/Cypress 또는 Browser MCP 시나리오를 기본 Docker 검증에 포함할지, 수동/선택만 할지? |
| Host OS | 이 compose를 Windows/macOS Docker에서 지원할지, Linux만 지원할지? |
| PostgreSQL versions | 로컬 Docker에서 **16** 외 버전(예: 15) 호환 테스트를 범위에 넣을지? |
| Auth / LDAP | 로컬 Docker 기본을 `AUTH_LOGIN_MODE=local`만 할지, AD/LDAP 사이드카 문서를 선택으로 둘지? |
| Image registry | 이미지를 로컬 빌드만 할지, 사내 레지스트리 푸시·태깅 규칙은? |
| Resource limits | 개발용 Postgres·앱 컨테이너 CPU/메모리 상한? |

---

**관련 요구사항**: [`docs/requirements/20260413-docker-local-dist-multidb.md`](../requirements/20260413-docker-local-dist-multidb.md)  
**환경·포트 계약**: [`docs/contract.md`](../contract.md)
