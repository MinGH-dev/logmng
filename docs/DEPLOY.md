# LogMng 배포 가이드

운영·폐쇄망 서버에 올리기 위한 **산출물 생성**과 **실행 시 환경 변수**를 정리합니다. **환경 변수 이름·포트·3분할 DB·비대화형 설치**의 단일 권위는 [`contract.md`](contract.md)입니다. DB 프로비저닝·`SETUP_MODE`·멱등은 [`../backend/DB_SETUP_GUIDE.md`](../backend/DB_SETUP_GUIDE.md), 루트 템플릿은 [`.env.example`](../.env.example)을 참고하세요.

## 1. 빌드 PC 요구 사항

| 도구 | 용도 |
|------|------|
| JDK 17+ | Maven·실행 검증 |
| Maven 3.x | 백엔드·정적 서버 JAR |
| Node.js + npm | 프론트 `npm run build` |

## 2. 산출물 만들기

프로젝트 **루트**에서:

```bash
chmod +x scripts/release-build.sh scripts/package-airgap-bin.sh scripts/build-offline-bundle.sh
```

### 2.1 폐쇄망 단일 tarball (**기본**)

인자 없이 실행하면 오프라인 번들을 만듭니다. DB 스크립트·`install-offline.sh`·문서까지 포함한 `dist/logmng-offline-${VERSION}.tar.gz` + `bin/` 이 채워집니다.

```bash
./scripts/release-build.sh
# 동일: ./scripts/release-build.sh offline
# 동일: ./scripts/build-offline-bundle.sh
```

선택:

```bash
VERSION=1.0.2 ./scripts/release-build.sh
REACT_APP_API_BASE_URL=http://127.0.0.1:9200/api ./scripts/release-build.sh
NO_TAR=1 ./scripts/release-build.sh   # dist/ 에 디렉터리만, tar 생략
```

### 2.2 `bin/` 만 필요 (빠른 반복 빌드)

tar 생략·용량 절약이 필요할 때만 `bin` 을 명시합니다.

```bash
./scripts/release-build.sh bin
# 동일: ./scripts/package-airgap-bin.sh
```

선택: Debian/Ubuntu용 `psql` 클라이언트 `.deb` 를 번들에 넣으려면 먼저 `./scripts/download-psql-for-bundle.sh` 실행 후 `offline` 빌드.

배포 서버에서는 tarball 풀고 [`../scripts/offline-bundle/README-OFFLINE.md`](../scripts/offline-bundle/README-OFFLINE.md) 의 `./install-offline.sh all` 흐름을 사용합니다. 백엔드가 다른 서버에만 있을 때는 `./install-offline.sh start-frontend` 로 UI만 띄울 수 있습니다.

## 3. 배포 서버 요구 사항

- **JRE/JDK 17+** (백엔드·정적 UI JAR 실행)
- **PostgreSQL** (스키마·DB는 DB_SETUP_GUIDE / `backend/src/main/resources/db/setup.sh`)
- **방화벽**: 백엔드 포트(기본 9200), UI 포트(기본 3001) 허용

### 3.1 운영 3분할 DB (물리 인스턴스·database 분리)

운영에서 PostgreSQL을 **최대 세 축**으로 나눌 수 있습니다: **시스템 Primary(A)**, **PB FEP 전용(A′)**, **ImageLog(B)**. JDBC는 각각 `SPRING_DATASOURCE_*`, `APP_DATASOURCE_PB_*`, `APP_DATASOURCE_IMAGELOG_*`로 지정합니다(URL이 비어 있거나 Primary와 같으면 해당 역할은 Primary 풀을 재사용 — 상세는 [`contract.md`](contract.md) 멀티 데이터소스 절).

**프로비저닝**은 `SETUP_MODE`로 한 번에(`full`) 또는 **독립 실행**으로 나눕니다.

| `SETUP_MODE` | 용도 |
|--------------|------|
| `full` | A·B·(split-PB 시) PB DB까지 `setup.sh`가 담당 |
| `sys_only` | 시스템 DDL/마이그레이션 위주; split-PB 구성에서는 **PB DB DDL이 이 실행만으로는 돌지 않음** → PB는 `pb_only`로 별도 호출 |
| `pb_only` | PB 전용 DB만; **`DB_PB_NAME` 필수**. A/ImageLog 단계 없음 |

저장소 루트에서 **비대화형**으로 DB만 올릴 때: `.env` 준비 후 `INSTALL_NONINTERACTIVE=1 ./scripts/install_linux.sh` ([`QUICK_START.md`](QUICK_START.md), [`.env.example`](../.env.example)). `.env`는 **`chmod 600`**, **커밋 금지**.

## 4. 환경 변수 (요약)

### 백엔드 (`bin/backend/run.sh`)

| 변수 | 설명 |
|------|------|
| `SPRING_DATASOURCE_URL` / `USERNAME` / `PASSWORD` | Primary JDBC |
| `APP_DATASOURCE_PB_URL` / `USERNAME` / `PASSWORD` | PB 전용 풀(선택; 비우면 Primary+스키마 계약) — [`contract.md`](contract.md) 표 |
| `APP_DATASOURCE_IMAGELOG_*` | ImageLog 전용 풀(선택) |
| `SERVER_PORT` | HTTP 포트 (기본 9200) |
| **`LOGGING_FILE_NAME`** | Spring Boot → `logging.file.name`. **미설정 시** 기본 `logs/application.log` (`application.yml`과 동일). 운영에서는 절대 경로(예 `/var/log/logmng/application.log`) 권장 |
| `LOG_DIR` | `run.sh start` 시 nohup 래퍼 로그 디렉터리(기본 `bin/backend/logs`). Spring **파일** 로그 경로는 `LOGGING_FILE_NAME` |
| `CORS_ALLOWED_ORIGINS` | 브라우저 Origin 목록(쉼표). UI 주소와 **정확히** 일치 |
| `APP_DB_SCHEMA_SYS` / `APP_DB_SCHEMA_PB` / `APP_DB_SCHEMA_IMAGELOG` | Hikari `search_path` 초기화 — `setup.sh`의 `SCHEMA_*` 와 일치 |
| **`ENCRYPTION_KEY`** (또는 `APP_SECURITY_ENCRYPTION_KEY`) | 이미지 로그 등 **AES-256 암·복호화** 키. UTF-8 기준 **32바이트** 권장 (`application.yml` → `app.security.encryption-key`) |
| `DECRYPTION_ENABLED` / `APP_DECRYPTION_ENABLED` | 복호화 기능 on/off (기본 `true`) |
| `AUTO_DECRYPT_ON_KEYWORD_SEARCH` / `APP_DECRYPTION_AUTO_DECRYPT_ON_KEYWORD_SEARCH` | 키워드 검색 시 자동 복호화 (기본 `true`) |
| `FAILURE_HANDLING` / `APP_DECRYPTION_FAILURE_HANDLING` | 복호화 실패 시 `fallback` \| `skip` \| `error` (기본 `fallback`) |

### 프론트 (`bin/frontend/run.sh`)

| 변수 | 설명 |
|------|------|
| `PORT` | 정적 서버 포트 (기본 3001) |
| **`LOGMNG_API_BASE_URL`** | 브라우저가 호출할 API 베이스 (예 `http://서버IP:9200/api`). **재빌드 없이** `/runtime-config.js` 로 주입 |
| `REACT_APP_API_BASE_URL` | 정적 JAR 프로세스에만 두면 `runtime-config` 생성 시 동일 후보로 사용 가능 |

`install-offline.sh configure` 는 위 값들을 `var/logmng.env` 에 모읍니다.

### 4.1 `bin/*/run.sh` 생명주기

배포 번들의 `bin/backend/run.sh`, `bin/frontend/run.sh`는 동일한 패턴으로 **백그라운드 기동·중지·상태**를 지원합니다.

| 명령 | 설명 |
|------|------|
| `./run.sh` | 포그라운드(터미널 점유) 실행 |
| `./run.sh start` | nohup 백그라운드 기동 |
| `./run.sh stop` | PID 파일 및 동일 포트 리스너 정리 |
| `./run.sh status` | 리스닝 여부 출력(미기동 시 비0 종료 코드) |

자세한 예시는 [`../bin/README.md`](../bin/README.md)를 참고하세요.

## 5. 기동 후 점검

```bash
curl -sS "http://127.0.0.1:9200/api/health"
```

UI에서 로그인 전 DevTools → Network 로 `/runtime-config.js` 와 `/api/auth/login` 대상 호스트가 기대와 같은지 확인합니다.

## 6. 산출물 무결성

오프라인 번들에는 `MANIFEST.txt`, `BUNDLE-VERSION.txt` 가 포함됩니다. 버전·빌드 시각·git 커밋(가능 시)을 기록합니다.

## 7. Git과 릴리스

`bin/**/*.jar`, `bin/frontend/www/**`, `dist/**` 는 용량 때문에 `.gitignore` 됩니다. **릴리스 아티팩트**는 CI·빌드 머신에서 `release-build.sh` 로 생성한 파일을 tarball/저장소로 보관합니다.
