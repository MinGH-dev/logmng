# 빠른 시작 가이드

## 서버·운영: 비대화형 DB 설치 (권장 순서)

로컬 개발이 아니라 **Linux 서버에서 PostgreSQL만 준비된 상태**로 스키마를 한 번에 올릴 때는 아래를 먼저 따릅니다.

1. **환경 파일 준비** (저장소 루트)

   ```bash
   cp .env.example .env
   # 편집기로 .env 수정 — 모든 플레이스홀더를 실제 값으로 교체
   chmod 600 .env
   ```

   - `.env`는 **커밋하지 않습니다** (`.gitignore`에 포함). 권한은 **`chmod 600`** 권장.
   - 변수 설명·필수 조합은 루트 **`.env.example`**(한글 주석)과 권위 문서 **`docs/contract.md`** 를 참고하세요.
   - `SETUP_MODE=full`일 때 **`DB_ETL_USER` / `DB_ETL_PASSWORD`** 는 `setup.sh` 비대화형에서 **필수**입니다 (`install_linux.sh`는 사전 검사하지 않으므로 `.env`에 반드시 넣으세요).

2. **비대화형 설치 실행** (저장소 루트)

   ```bash
   INSTALL_NONINTERACTIVE=1 ./scripts/install_linux.sh
   ```

   - 다른 경로의 env를 쓰려면: `INSTALL_ENV_FILE=/path/to/env INSTALL_NONINTERACTIVE=1 ./scripts/install_linux.sh`
   - 설치 후 JDBC용 export 파일을 자동 생성하려면 `.env`에 `INSTALL_WRITE_APP_ENV=1` 등을 설정할 수 있습니다(기본 출력: `backend/.env.logmng.generated`). 생성물도 비밀이므로 커밋 금지.

3. **DB 상세·3분할·`sys_only` / `pb_only` 독립 실행** → [`../backend/DB_SETUP_GUIDE.md`](../backend/DB_SETUP_GUIDE.md), 계약·env 표 → [`contract.md`](contract.md).

4. **배포 번들·폐쇄망** → [`DEPLOY.md`](DEPLOY.md), [`../bin/README.md`](../bin/README.md), [`../scripts/offline-bundle/README-OFFLINE.md`](../scripts/offline-bundle/README-OFFLINE.md).

**Docker로 로컬 스택**을 올릴 때는 오프라인 번들 트리 `dist/logmng-offline-<VERSION>/`를 기준으로 `docker compose`를 사용합니다. 절차·헬스 확인·테스트 이미지(`mvn test`, TC-08)는 **[docker/README.md](docker/README.md)** 를 따릅니다.

---

## 개발 시작하기

### 1. 요건 수집 및 분석
- 사용자 요건 확인
- 기존 코드 분석
- 영향도 분석
- **요건 문서 작성** (`docs/requirements/yyyyMMdd-요건명.md`)

### 2. 개발 준비
```bash
# Git 브랜치 생성
git checkout -b feat/요건명

# 기존 파일 백업 (필요 시)
cp src/components/LogGrid.js src/components/LogGrid.old.js
```

### 3. 개발 및 검증

#### 프론트엔드 개발
```bash
cd frontend
npm start
```

**검증:**
- 브라우저 콘솔에서 API 요청 파라미터 확인
- `console.log`로 전송 데이터 확인

#### 백엔드 개발
```bash
cd backend
mvn clean package -DskipTests
java -jar target/logmng-backend-1.0.1.jar
```

**검증:**
```bash
# API 테스트
curl -X POST http://localhost:9200/api/logs/db-refactored/search \
  -H "Content-Type: application/json" \
  -d '{"logType": "java_fw_imglog", ...}'

# 로그 확인 (기본 파일 경로는 LOGGING_FILE_NAME 미설정 시 application.yml 기준)
tail -f backend/logs/application.log
```

**로컬 프로세스 관리:** `./scripts/dev-services.sh <frontend|backend|db|all> <start|stop|restart|status>` — 예: `./scripts/dev-services.sh all status`

### 4. 문제 발생 시

#### 요건 반영 ✅, Side Effect ❌
1. 즉시 롤백
2. 영향도 분석 재수행
3. 개선된 코드 작성

#### 요건 반영 ❌
1. 신규 코드 개선
2. 요건 재분석
3. 재검증

### 5. 문서화
- 요건 문서 업데이트
- 테스트 결과 기록
- 체크리스트 완료

## 상세 가이드

- [개발 워크플로우 가이드](./DEVELOPMENT_WORKFLOW.md) - 전체 개발 프로세스
- [요건 문서](./requirements/) - 각 요건별 상세 문서

## 폐쇄망 배포 번들 (`bin/`)

인터넷이 있는 빌드 머신에서 저장소 루트에서:

```bash
./scripts/package-airgap-bin.sh
```

생성물·실행 방법: [`bin/README.md`](../bin/README.md). 계약 환경 변수: `docs/contract.md`(예: `CORS_ALLOWED_ORIGINS`).

**폐쇄망 서버에서 설치·기동을 한 스크립트로** 하려면 tarball까지 만든 뒤 서버에서 풀고 `./install-offline.sh all`:

```bash
# (선택) 폐쇄망에서 psql 없을 때 쓸 Debian/Ubuntu amd64 클라이언트 .deb 를 번들에 넣기
# ./scripts/download-psql-for-bundle.sh
./scripts/build-offline-bundle.sh
# dist/logmng-offline-1.0.0.tar.gz 를 폐쇄망으로 복사 후
# tar xzf logmng-offline-1.0.0.tar.gz && cd logmng-offline-1.0.0 && ./install-offline.sh all
```

설명: [`scripts/offline-bundle/README-OFFLINE.md`](../scripts/offline-bundle/README-OFFLINE.md).

운영·폐쇄망 배포 절차·환경 변수 요약: [`DEPLOY.md`](DEPLOY.md). 한 줄 빌드(기본 = 오프라인 tarball): `./scripts/release-build.sh` — `bin/` 만: `./scripts/release-build.sh bin`.

## 중요 원칙

1. **기존 코드를 직접 수정하지 마세요**
2. **검증 없이 배포하지 마세요**
3. **요건 문서를 반드시 작성하세요**
4. **문제 발생 시 즉시 롤백하세요**
