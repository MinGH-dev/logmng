# 빠른 시작 가이드

## 🚀 개발 시작하기

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
cd dev/frontend
npm start
```

**검증:**
- 브라우저 콘솔에서 API 요청 파라미터 확인
- `console.log`로 전송 데이터 확인

#### 백엔드 개발
```bash
cd dev/backend
mvn clean package -DskipTests
java -jar target/logmng-backend-1.0.0.jar
```

**검증:**
```bash
# API 테스트
curl -X POST http://localhost:9200/api/logs/db-refactored/search \
  -H "Content-Type: application/json" \
  -d '{"logType": "java_fw_imglog", ...}'

# 로그 확인
tail -f dev/backend/logs/application.log
```

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

## 📚 상세 가이드

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

## ⚠️ 중요 원칙

1. **기존 코드를 직접 수정하지 마세요**
2. **검증 없이 배포하지 마세요**
3. **요건 문서를 반드시 작성하세요**
4. **문제 발생 시 즉시 롤백하세요**





