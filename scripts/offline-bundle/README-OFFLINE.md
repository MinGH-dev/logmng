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
| `tools/psql-deb/` | (선택) Debian bookworm amd64용 PostgreSQL **16** `psql` `.deb` 묶음(PGDG) — 빌드 PC에서 `scripts/download-psql-for-bundle.sh` 후 번들 제작 시 포함 |
| `tools/psql-rpm-el9/` | (선택) RHEL / Rocky / Alma **9.6** x86_64용 PostgreSQL **16** `psql` `.rpm` 묶음(PGDG `rhel-9.6-x86_64`) — `scripts/download-psql-rpm-el9.sh` 후 번들 제작 시 포함 |

**포함하지 않는 것**: JVM(JDK/JRE), PostgreSQL **서버** — OS/인프라에 별도 설치. **`psql` 클라이언트**는 OS에 없을 수 있습니다. **앱 서버만** 두고 **DB는 원격**인 경우, DDL 적용을 이 서버에서 할 때 `psql`이 필요하므로, 번들에 `tools/psql-deb/*.deb`(Debian/Ubuntu) 또는 `tools/psql-rpm-el9/*.rpm`(EL 9.6)을 넣어 두면 `./install-offline.sh db` 또는 `./install-offline.sh install-psql` 이 **클라이언트 설치를 자동 시도**합니다(아래 참고).

## 전제

- **이 tarball/디렉터리 안에는** 애플리케이션 JAR·정적 UI·DB SQL·설치 스크립트만 포함됩니다. **인터넷이 필요 없습니다.**
- **번들 제작**은 인터넷·Maven·npm이 있는 빌드 PC에서 **한 번** `scripts/build-offline-bundle.sh` 로 수행합니다 (저장소 루트). **앱 서버에 `psql`을 자동 설치하려면** 대상 OS에 맞게 먼저 `./scripts/download-psql-for-bundle.sh`(Debian/Ubuntu) 또는 `./scripts/download-psql-rpm-el9.sh`(RHEL/Rocky/Alma 9.6)를 실행한 뒤 번들을 만들어 `tools/psql-deb/` 또는 `tools/psql-rpm-el9/`에 패키지가 들어가게 하세요.
- **앱 서버(설치 실행 호스트)** 사전 요구: **JDK/JRE 17+**, `bash`. DB는 **원격**이어도 되며, 이 호스트에서 `db/setup.sh`를 돌릴 때는 **`psql` 클라이언트**가 PATH에 있거나 번들 `.deb`로 설치 가능해야 합니다.
- `./install-offline.sh all` 에서 DB 단계(옵션 1·2)를 이 서버에서 수행하면 **`install-psql` → `db` 순으로 psql이 준비**됩니다(또는 `db` 안에서 자동 설치 시도).

### Java는 설치만 하고 PATH에 없을 때

다음 중 하나면 `install-offline.sh check` / `start`가 동작합니다.

1. 셸에서 (영구 반영은 `~/.bashrc` 등에 추가):
   ```bash
   export JAVA_HOME=/실제/jdk/경로    # 예: /usr/lib/jvm/java-17-openjdk-amd64
   export PATH="$JAVA_HOME/bin:$PATH"
   ```
2. 또는 **`configure` 단계**에서 묻는 **java 전체 경로**에 `.../bin/java` 를 넣으면 `var/logmng.env`에 `JAVA_CMD`로 저장됩니다.
3. 또는 수동으로: `export JAVA_CMD=/path/to/bin/java` 후 `./install-offline.sh start`

### psql(PostgreSQL 클라이언트) 설치

번들의 `db/setup.sh`·`check-db.sh`는 내부에서 **`psql`** 을 호출합니다. **DB 서버는 원격**이어도, DDL을 **앱 서버에서** 적용한다면 그 앱 서버에 클라이언트가 있어야 합니다.

- **권장(폐쇄망 앱 서버)**: 빌드 시 `download-psql-for-bundle.sh`(`.deb`) 또는 `download-psql-rpm-el9.sh`(`.rpm`)로 클라이언트 포함 → 배포 후 `./install-offline.sh install-psql` 또는 `./install-offline.sh db` 시 자동 설치(sudo/root, `dpkg` 또는 `dnf`/`yum`/`rpm`).
- **자동 설치 끄기**: `SKIP_BUNDLE_PSQL=1` 환경 변수.

#### 번들에 포함된 `.deb`로 설치 (Debian / Ubuntu 계열, amd64)

빌드 PC(인터넷 있음)에서:

```bash
./scripts/download-psql-for-bundle.sh
./scripts/build-offline-bundle.sh
```

생성된 tarball에는 `tools/psql-deb/*.deb`(PostgreSQL **16** `psql`, PGDG bookworm amd64 + `libpq5`·`postgresql-client-common`)가 들어갑니다. **폐쇄망 설치 서버**에서 `./install-offline.sh db`(또는 `all`에서 DB 예) 실행 시 **`psql`이 없으면** `sudo dpkg -i`로 위 패키지 설치를 **자동 시도**합니다. 최소 이미지에서는 `libssl`, `libreadline` 등 **추가 의존 패키지**가 필요할 수 있으며, 같은 OS의 오프라인 미러에서 맞춰 설치해야 합니다. 자동 설치를 끄려면 `export SKIP_BUNDLE_PSQL=1` .

#### 번들에 포함된 `.rpm`으로 설치 (RHEL / Rocky / Alma 9.6, x86_64)

빌드 PC(인터넷 있음)에서:

```bash
./scripts/download-psql-rpm-el9.sh
./scripts/build-offline-bundle.sh
```

생성된 tarball에는 `tools/psql-rpm-el9/*.rpm`(PostgreSQL **16** 클라이언트용 PGDG RPM, `rhel-9.6-x86_64` 리포지토리)이 들어갑니다. **폐쇄망 설치 서버**에서 `psql`이 없으면 `dnf`/`yum`으로 로컬 RPM 설치를 **자동 시도**합니다(`postgresql16-libs` 후 `postgresql16` 순). **9.6 미만/다른 아키텍처**는 이 RPM과 맞지 않을 수 있으므로, 해당 환경에서는 OS 미러의 클라이언트 패키지를 쓰거나 PGDG 경로에 맞는 스크립트로 RPM을 준비하세요.

#### 패키지 관리자로 설치 (일반)

**폐쇄망**이면 OS 배포판과 **동일 메이저 버전**의 클라이언트 패키지를 내부 yum/apt 저장소(또는 USB로 옮긴 `.rpm`/`.deb`)로 설치하세요. 서버 PostgreSQL과 클라이언트 major 버전을 맞추는 것이 안전합니다.

**Debian / Ubuntu 계열** (패키지 이름 예):

```bash
sudo apt install postgresql-client
# 또는 특정 버전이 필요하면 내부 미러의 postgresql-client-16 등
```

**RHEL / Rocky / Alma 계열** (예):

```bash
sudo dnf install postgresql
# 또는 postgresql16 등 모듈/저장소에 맞는 클라이언트 패키지
```

설치 후 `psql --version` 과 `which psql` 로 PATH를 확인합니다.

#### 예외: psql 없이 가는 경우

| 작업 | psql |
|------|------|
| DB·스키마를 **다른 호스트/도구**로 이미 만들었고, 여기서는 `configure` + `start` 만 | **불필요** |
| `./install-offline.sh db` 또는 `all`에서 **이 서버에서** `setup.sh` 실행 | **필요** |

다른 PC에만 `psql`이 있으면, 그 PC에서 번들의 `db/` 스크립트를 수동 실행한 뒤 설치 서버에서는 `all` 할 때 **DB 단계만 건너뛰면** 됩니다.

### 폐쇄망: 승인 스냅샷·복호화 허용 테이블만 갱신 (20260320)

기존 운영 DB에 `search_history_approved_row.row_status`가 없어 복호화 승인 시 PostgreSQL **42703**이 나는 경우, **`db/airgap-only-20260320-sys-decryption-composite-pk.sql`** 만 실행하면 됩니다. (`migrate-sys-decryption-composite-pk-20260320.sql`과 동일 본문, 단일 파일 배포용.)

```bash
psql -v ON_ERROR_STOP=1 -h 호스트 -p 포트 -U 관리계정 -d DB이름 \
  -c "SET search_path TO 스키마_SYS, 스키마_PB, public;" \
  -f db/airgap-only-20260320-sys-decryption-composite-pk.sql
```

`schema_sys.sql`은 신규 설치용 DDL에 이미 반영되어 있으므로, **레거시 DB에만** 위 파일을 적용합니다.

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

**시드 SQL만 생략(DDL·마이그레이션은 유지)**: 모드 **1**(full)에서 묻는 `Apply seed SQL …? [Y/n]`에 **n**을 주거나, 미리 `export SKIP_INIT_DATA=1` 후 `db`를 실행하면 `setup.sh` 5단계(`INIT_DATA_FILE`: `init-data.sql` 또는 폐쇄망 `init-data-closed-network-admin-only.sql`)가 실행되지 않습니다. 사용자·권한 등은 별도 프로비저닝이 필요합니다. 모드 **2**(`sys_only`)는 기본적으로 시드를 넣지 않습니다(`SYS_ONLY_LOAD_INIT_DATA=1`로 예외).
| `./install-offline.sh configure` | `var/logmng.env` 생성(비밀번호·CORS·포트 등) |
| `./install-offline.sh start` | 환경 로드 후 백엔드·UI 기동 |
| `./install-offline.sh start-frontend` | **UI만** 기동 (백엔드는 다른 호스트 등에 둘 때). `configure`로 만든 `var/logmng.env`가 있으면 `FRONTEND_PORT`·`LOGMNG_API_BASE_URL`·`JAVA_CMD` 반영; 없으면 셸 환경만 사용 |
| `./install-offline.sh stop-frontend` | UI 프로세스만 종료 (`var/run/frontend.pid`) |
| `./install-offline.sh stop` | 백엔드·UI pid 모두 종료 |
| `./install-offline.sh status` | 프로세스·포트 안내 |
| `./install-offline.sh all` | 위 순서 통합 마법사 |

**프론트만(스크립트 없이)**: 번들 루트에서 `cd bin/frontend && export LOGMNG_API_BASE_URL='http://백엔드:9200/api' && PORT=3001 ./run.sh` — pid 파일은 쓰지 않으므로 종료는 프로세스에 직접 `kill` 합니다.

로그: `var/log/backend.log`, `var/log/frontend.log`  
PID: `var/run/backend.pid`, `var/run/frontend.pid`  
설정: `var/logmng.env` (권한 600, 비밀 포함)

### PB·ImageLog 전용 JDBC (`var/logmng.env`)

폐쇄망에서 `configure`로 만든 **`var/logmng.env`** 에 **PB FEP 전용**·**ImageLog 전용** JDBC URL(및 필요 시 사용자·비밀번호)을 넣을 수 있습니다. 백엔드는 ImageLog에 대해는 URL이 있을 때 **별도 HikariCP 풀**을 만들고, URL이 **비어 있으면** Primary(`SPRING_DATASOURCE_*` 등) **동일 풀**을 재사용합니다. **PB 전용 데이터소스**가 포함된 배포에서는 동일 패턴이 적용됩니다: **`APP_DATASOURCE_PB_URL`을 비우면** PB FEP도 Primary 풀에서만 접속하며, **`APP_DB_SCHEMA_SYS` / `APP_DB_SCHEMA_PB`** 로 스키마만 나눕니다(단일 인스턴스·스키마 분리). 자세한 변수명은 번들 내 **`docs/contract.md`** 를 확인합니다.

**암·복호화**: `configure` 시 `ENCRYPTION_KEY` 등을 묻고 `var/logmng.env`에 기록합니다. 백엔드는 `ENCRYPTION_KEY` 또는 Spring 규약 `APP_SECURITY_ENCRYPTION_KEY`로 `app.security.encryption-key`를 덮어쓸 수 있습니다. 복호화 동작은 `DECRYPTION_ENABLED`, `AUTO_DECRYPT_ON_KEYWORD_SEARCH`, `FAILURE_HANDLING`(또는 `APP_DECRYPTION_*` 대체 이름)로 조정합니다. 자세한 변수 표는 저장소 루트 `docs/DEPLOY.md` §4.

### PB FEP를 시스템 DB와 다른 PostgreSQL database에 둘 때

`./install-offline.sh db`(또는 `all`에서 DB 예)는 **PB를 DB A와 분리**할지 묻고, 분리 시 `db/setup.sh` 자식 프로세스에 다음을 넘깁니다(머리말·`backend/DB_SETUP_GUIDE.md`와 동일한 이름).

| 변수 | 설명 |
|------|------|
| `DB_PB_NAME` | PB 전용 database 이름(설정 시 분리 모드). 비우거나 프롬프트에서 N이면 레거시 단일 DB(A에 PB DDL) 흐름. |
| `DB_PB_HOST` / `DB_PB_PORT` | 선택; 기본은 DB A와 동일(`DB_HOST` / `DB_PORT`). |
| `DB_PB_SUPERUSER` | 선택; 기본은 `DB_SUPERUSER`. |

번들 내 `setup.sh`가 분리 모드를 지원하면: **A에서는 PB DDL을 생략**하고 **PB database에서 PB DDL·연계 단계**를 적용합니다. **PB DB만** 채우는 재실행(이미 A 적용됨)은 `SETUP_MODE=pb_only`로 동일 변수를 넘겨 실행합니다(수동·자동화 시). 애플리케이션은 **`APP_DATASOURCE_PB_URL`**(및 필요 시 `APP_DATASOURCE_PB_USERNAME` / `APP_DATASOURCE_PB_PASSWORD`)을 PB 쪽 JDBC로 **`configure`와 일치**시켜야 합니다. 비우면 Primary 풀만 쓰므로 **분리 운영에서는 URL을 반드시 지정**합니다.

## API URL과 프론트

1. **런타임(재빌드 없음)** — JDK 정적 서버 JAR는 `GET /runtime-config.js` 로 `window.__LOGMNG_RUNTIME_CONFIG__.apiBaseUrl` 을 내려줍니다. 값은 프로세스 환경 변수 **`LOGMNG_API_BASE_URL`** (또는 `REACT_APP_API_BASE_URL`) 에서 읽습니다. `install-offline.sh configure` 에서 `LOGMNG_API_BASE_URL` 을 묻고 `var/logmng.env`에 저장하며, `start` 시 정적 서버 자식 프로세스에 전달됩니다. 비우면 UI는 빌드 시 박힌 `REACT_APP_API_BASE_URL` 또는 기본 `http://localhost:9200/api` 를 씁니다.
2. **빌드 타임** — 여전히 `REACT_APP_API_BASE_URL=... npm run build` 로 기본값을 박을 수 있습니다. 런타임 값이 있으면 그쪽이 우선합니다.
3. **수동** — `www/runtime-config.js` 를 편집해 `apiBaseUrl` 을 넣을 수도 있습니다(다른 정적 서버 사용 시).

## CORS

UI를 `http://서버:3001` 로 열면 백엔드에 `CORS_ALLOWED_ORIGINS`에 동일 Origin을 넣어야 합니다. `configure` 단계에서 묻습니다.
