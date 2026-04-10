# 배포 전용 `bin` 번들 (개발과 분리)

| 구분 | 경로 / 방법 |
|------|-------------|
| **일상 개발** | `frontend/` + `npm start`, `backend/` + `mvn` + `scripts/dev-services.sh` 등 — **소스 트리 기준** |
| **배포·폐쇄망** | 이 `bin/` 아래 모듈만 복사해 실행 — **빌드 산출물만** |

온라인 **빌드 PC**에서 `scripts/package-airgap-bin.sh`를 실행하면 `bin/backend`·`bin/frontend`에 **실행 모듈**(JAR + 정적 `www/`)이 채워집니다.  
**배포 서버**(폐쇄망)에는 **JRE 17+**, **PostgreSQL**, 채워진 `bin` 디렉터리만 있으면 됩니다(Node/Maven 불필요).

## 모듈 구조

| 경로 | 설명 |
|------|------|
| `backend/logmng-backend-1.0.1.jar` | Spring Boot fat JAR. *`package-airgap-bin.sh` 실행 후 생성* (Git 무시) |
| `backend/run.sh` | 백엔드 기동·중지·상태: `./run.sh` (포그라운드), `./run.sh start` / `stop` / `status` (TC-07) |
| `backend/MODULES.md` | 이 모듈 요약 |
| `frontend/logmng-static-server-1.0.0.jar` | JDK 전용 정적 서버 JAR. *스크립트 후 생성* (Git 무시) |
| `frontend/www/` | `npm run build` 결과. *스크립트 후 생성* (내용은 Git 무시, `www/.gitkeep`만 추적) |
| `frontend/run.sh` | UI 기동(기본 포트 3001); `start` / `stop` / `status` 동일 |
| `frontend/MODULES.md` | 이 모듈 요약 |

> **Git**: JAR와 `www/` 빌드물은 용량 때문에 `.gitignore` 처리합니다. 릴리스는 tarball·아티팩트 저장소로 전달하고, CI/빌드 머신에서 `package-airgap-bin.sh`로 채우면 됩니다.

### 폐쇄망 “한 도구로 설치·기동”

인터넷 **있는** 빌드 PC에서 `./scripts/build-offline-bundle.sh` → `dist/logmng-offline-1.0.0.tar.gz` 생성.  
폐쇄망 서버에서는 tarball 풀고 **`./install-offline.sh all`** 만으로 DB(선택)·설정·기동까지 처리합니다. 상세: 번들 루트의 `README-OFFLINE.md`.

## 빌드(인터넷 있는 환경)

저장소 루트에서:

```bash
chmod +x scripts/package-airgap-bin.sh bin/backend/run.sh bin/frontend/run.sh
./scripts/package-airgap-bin.sh
```

백엔드 API 주소를 번들에 박으려면(프론트 빌드 타임):

```bash
REACT_APP_API_BASE_URL=http://서버IP:9200/api ./scripts/package-airgap-bin.sh
```

## 폐쇄망 서버에서 실행

1. **PostgreSQL** 준비 후 `backend/DB_SETUP_GUIDE.md` 등에 따라 스키마 적용.
2. **백엔드** (예: 9200)

   ```bash
   cd bin/backend
   export SPRING_DATASOURCE_URL='jdbc:postgresql://호스트:5432/logmng'
   export SPRING_DATASOURCE_USERNAME='...'
   export SPRING_DATASOURCE_PASSWORD='...'
   # 선택: PB FEP·ImageLog를 **별도 PostgreSQL 인스턴스**에 두는 경우 전용 JDBC URL(및 사용자·비밀번호).
   #   APP_DATASOURCE_PB_URL — PB 전용 풀(비우면 Primary 풀 + APP_DB_SCHEMA_SYS / APP_DB_SCHEMA_PB 스키마 분리).
   #   APP_DATASOURCE_IMAGELOG_URL — ImageLog 전용 풀(비우면 Primary 풀 재사용).
   # 정식 env 이름·부가 키: docs/contract.md
   # 멀티 스키마: docs/contract.md / application.yml (APP_DB_SCHEMA_* 등)
   # 브라우저 UI 출처가 localhost가 아니면 CORS:
   export CORS_ALLOWED_ORIGINS='http://서버IP:3001'
   # 암·복호화(이미지 로그 등): 운영에서는 반드시 고유 키(UTF-8 32바이트 권장)
   export ENCRYPTION_KEY='32바이트_길이의_비밀키문자열!!!!!!!!'
   # 선택: export DECRYPTION_ENABLED=true FAILURE_HANDLING=fallback
   # Spring 로그 파일 경로(선택): export LOGGING_FILE_NAME=/var/log/logmng/application.log
   ./run.sh
   # 또는 백그라운드: ./run.sh start — 중지 ./run.sh stop — 상태 ./run.sh status
   ```

3. **프론트** (예: 3001)

   ```bash
   cd bin/frontend
   export LOGMNG_API_BASE_URL='http://브라우저가 접속할-백엔드-호스트:9200/api'
   PORT=3001 ./run.sh
   ```

`LOGMNG_API_BASE_URL` 은 **재빌드 없이** 정적 JAR가 `/runtime-config.js`로 내려주는 API 베이스(우선순위가 `REACT_APP_API_BASE_URL` 빌드값보다 높음). 비우면 빌드 시 박힌 값·기본 localhost를 씁니다.

UI와 API가 **다른 호스트/포트**이면 CORS에 **브라우저가 실제로 여는 UI URL**(Origin)을 넣어야 합니다.

## 제한 사항

- **JRE/JDK 17+**는 배포 서버에 별도 설치(또는 동일 버전 포터블 JDK 포함 tarball)가 필요합니다. 이 저장소는 JVM 본체를 포함하지 않습니다.
- **DB 드라이버**는 백엔드 fat JAR에 포함됩니다.
- 프론트는 **빌드 시점**의 `REACT_APP_API_BASE_URL`이 JS에 고정됩니다. API 주소를 바꾸려면 해당 URL로 **다시** `package-airgap-bin.sh`를 실행해야 합니다.

## 백엔드 설정 파일(선택)

`run.sh` 뒤에 Spring 인자를 넘기거나, 같은 디렉터리에 `application-local.yml`을 두고:

```bash
java -jar logmng-backend-1.0.1.jar --spring.config.additional-location=file:./
```

(파일명·경로는 운영 표준에 맞게 조정)
