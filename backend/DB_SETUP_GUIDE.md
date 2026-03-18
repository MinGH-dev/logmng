# PostgreSQL 16 데이터베이스 설정 가이드

## 📋 개요

이 가이드는 PostgreSQL 16을 사용하여 로그 관리 시스템의 데이터베이스를 설정하는 방법을 설명합니다.

## 🔧 사전 요구사항

- PostgreSQL 16 설치 완료
- Homebrew (macOS)

## 🚀 설정 방법

### 1. PostgreSQL 서비스 시작

```bash
# PostgreSQL 16 서비스 시작
brew services start postgresql@16

# 서비스 상태 확인
brew services list | grep postgresql@16
```

### 2. 데이터베이스 및 사용자 생성

```bash
# PostgreSQL에 접속 (기본 postgres 사용자)
psql -U postgres

# 또는 비밀번호 없이 접속
psql postgres
```

PostgreSQL 프롬프트에서 다음 명령 실행:

```sql
-- 데이터베이스 생성
CREATE DATABASE logmng;

-- 사용자 생성
CREATE USER logmng WITH PASSWORD 'logmng123';

-- 권한 부여
GRANT ALL PRIVILEGES ON DATABASE logmng TO logmng;

-- 생성된 데이터베이스로 전환
\c logmng

-- 스키마 권한 부여
GRANT ALL PRIVILEGES ON SCHEMA public TO logmng;
```

### 3. 테이블 생성

```bash
# 스키마 파일 실행
cd dev/backend/src/main/resources/db
psql -U postgres -d logmng -f schema.sql
```

또는 PostgreSQL 프롬프트에서:

```sql
\c logmng
\i /Volumes/T7/dev/logmng_frontend/dev/backend/src/main/resources/db/schema.sql
```

### 4. 초기 데이터 삽입

```bash
# 초기 데이터 파일 실행
psql -U postgres -d logmng -f init-data.sql
```

### 5. 연결 테스트

```bash
# logmng 사용자로 연결 테스트
psql -U logmng -d logmng -h localhost -p 5432

# 테이블 확인
\dt

# 데이터 확인
SELECT COUNT(*) FROM pb_send;
SELECT COUNT(*) FROM pb_recv;
```

## 📊 테이블 구조

### pb_send (송신 로그 테이블)
- `id`: BIGSERIAL (Primary Key)
- `log_timestamp`: TIMESTAMP (로그 시간)
- `media_code`: VARCHAR(10) (매체코드)
- `tr_code`: VARCHAR(20) (거래코드)
- `user_id`: VARCHAR(50) (사용자ID)
- `ip_address`: VARCHAR(45) (IP주소)
- `user_agent`: TEXT (사용자에이전트)
- `request_data`: TEXT (요청데이터 - 암호화)
- `response_data`: TEXT (응답데이터 - 암호화)
- `status_code`: INTEGER (상태코드)
- `response_time`: INTEGER (응답시간)
- `error_message`: TEXT (오류메시지)
- `session_id`: VARCHAR(100) (세션ID)
- `device_type`: VARCHAR(20) (디바이스타입)
- `created_at`: TIMESTAMP (생성일시)
- `updated_at`: TIMESTAMP (수정일시)

### pb_recv (수신 로그 테이블)
송신 테이블과 동일한 구조를 가집니다.

## 🔍 인덱스

다음 인덱스가 자동으로 생성됩니다:
- `idx_pb_send_timestamp`: 로그 시간 인덱스
- `idx_pb_send_media_code`: 매체코드 인덱스
- `idx_pb_send_tr_code`: 거래코드 인덱스
- `idx_pb_send_user_id`: 사용자ID 인덱스
- `idx_pb_send_session_id`: 세션ID 인덱스
- `idx_pb_send_search`: 복합 인덱스 (timestamp, media_code, tr_code)
- 수신 테이블에도 동일한 인덱스 생성

## ⚙️ 백엔드 설정

`application.yml`에 다음 설정이 포함되어 있습니다:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/logmng
    username: logmng
    password: logmng123
    driver-class-name: org.postgresql.Driver
```

## 🔧 문제 해결

### PostgreSQL 서비스가 시작되지 않는 경우

```bash
# 서비스 재시작
brew services restart postgresql@16

# 로그 확인
tail -f /opt/homebrew/var/log/postgresql@16.log
```

### 연결 실패 시

1. PostgreSQL 서비스가 실행 중인지 확인:
   ```bash
   brew services list | grep postgresql@16
   ```

2. 포트 확인:
   ```bash
   lsof -ti:5432
   ```

3. 수동으로 서비스 시작:
   ```bash
   /opt/homebrew/opt/postgresql@16/bin/postgres -D /opt/homebrew/var/postgresql@16
   ```

### 권한 문제

```sql
-- 모든 권한 부여
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO logmng;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO logmng;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO logmng;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO logmng;
```

## 📝 참고사항

- 기본 비밀번호는 `logmng123`입니다. 프로덕션 환경에서는 반드시 변경하세요.
- 데이터베이스 이름은 `logmng`입니다.
- 사용자 이름은 `logmng`입니다.
- 포트는 기본값 `5432`를 사용합니다.

### search_history.user_id 규칙 및 마이그레이션

- `search_history.user_id`는 요청자의 **사용자 ID (numeric `app_user.id`)** 입니다. 조인 조건은 **app_user.id = search_history.user_id** 입니다. username을 저장하지 않습니다. (요건: 20260316-search-history-user-id-query-and-naming)
- **신규 설치**: schema.sql 적용 후 init-data.sql에서 search_history 시드는 app_user.id(숫자)를 사용합니다.
- **기존 DB (VARCHAR user_id)** : 사용자 승인 후 아래 마이그레이션을 한 번 실행하세요. 혼합 의미 검출, username→id·id::text→id 백필, 고아 행 처리(정책: 삭제 후 문서화), cutover·FK·인덱스 적용. idempotent.
- **실행 예 (프로젝트 루트 기준):**  
  `psql -U postgres -h localhost -p 5432 -d logmng -f backend/src/main/resources/db/migrate-search-history-user-id-to-bigint.sql`
- **복호화 실행 경로 (req 20260317-decrypt-execution-user-id-fix)**: POST /api/logs/decrypt 의 소유·승인 검사는 `search_history.user_id`(BIGINT)와 현재 사용자 id만 사용합니다. 이 경로를 사용하기 전에 **반드시** `migrate-search-history-user-id-to-bigint` 를 적용해 `search_history.user_id` 가 BIGINT인 상태로 두세요.
  - **증상**: `search_history.user_id`가 아직 VARCHAR이고 username 값이 들어 있는 경우, 코드는 Long(user id)로 비교하므로 매칭되지 않아 "복호화 거부(승인 미충족)"가 발생할 수 있습니다. 이 경우 위 마이그레이션을 실행하면 해결됩니다.
- 이전 스크립트 `migrate-search-history-user-id-to-username.sql`은 **legacy** 로 두지 않고 새 스키마로 정렬한 환경에서는 실행하지 마세요.

### 복호화 허용 저장소 (user_decryption_allowed, req 20260318)

- "누가 어떤 GUID를 복호화할 수 있는지"는 **user_decryption_allowed** 테이블에서만 판단한다. `search_history_approved_row`는 감사/이력용으로만 유지된다.
- **마이그레이션 적용** (기존 DB에 테이블 추가 및 선택적 백필):
  - 프로젝트 루트에서: `psql -U postgres -h localhost -p 5432 -d logmng -f backend/src/main/resources/db/migrate-user-decryption-allowed.sql`
  - 또는: `psql -U logmng -h localhost -p 5432 -d logmng -f backend/src/main/resources/db/migrate-user-decryption-allowed.sql`
  - idempotent: 테이블/인덱스는 IF NOT EXISTS; 백필은 재실행 시 ON CONFLICT로 덮어쓰므로 안전.
  - `search_history_approved_row`는 변경·삭제하지 않음.

### 복호화 403 시 점검 (req 20260317-image-log-decrypt-error-root-cause-and-data-validation)

"복호화 거부(승인 미충족)"(403 DECRYPTION_NOT_APPROVED)이 **마이그레이션 적용 후에도** 발생하면, 다음 진단 SQL로 해당 검색 이력 행의 `user_id`와 요청자(requester)의 `app_user.id`가 일치하는지 확인하세요.

1. **실패한 요청의 searchHistoryId**를 로그 또는 프론트에서 확인합니다.
2. **요청자(복호화를 실행하려는 사용자)의 로그인 ID(사용자명)** 또는 `app_user.id`를 확인합니다.
3. 아래 SQL에서 `:search_history_id`를 해당 ID로 바꿔 실행합니다.

```sql
-- 아래에서 <search_history_id>, <requester_username> 을 실제 값으로 바꿔 실행
-- 예: search_history_id=100, 요청자 로그인ID=user2
SELECT sh.id AS search_history_id,
       sh.user_id AS row_user_id,
       sh.approval_status,
       sh.expires_at,
       (sh.expires_at > CURRENT_TIMESTAMP) AS not_expired,
       au.id AS app_user_id,
       (sh.user_id = au.id) AS user_id_matches
FROM search_history sh
LEFT JOIN app_user au ON au.username = '<requester_username>'
WHERE sh.id = <search_history_id>;
```

- **해석**: `row_user_id`는 해당 검색 이력을 **요청한 사용자**의 `app_user.id`여야 합니다. 복호화 실행 시 **현재 로그인 사용자 id**와 `row_user_id`가 같아야 403이 발생하지 않습니다.
- `user_id_matches`가 false이면, 해당 검색 이력은 다른 사용자(요청자) 소유이므로 **현재 로그인한 사용자가 요청자가 아닐 때** 의도된 403입니다(실행자는 요청자만 가능).
- `row_user_id`가 요청자의 `app_user.id`와 다른데 요청자가 실행했다면, **데이터 불일치**이거나 **세션/currentUserId** 문제일 수 있으므로 백엔드 로그의 `복호화 승인 검사 실패(진단): searchHistoryId=..., currentUserId=..., reason=..., rowUserId=...` 로그로 `currentUserId`와 `rowUserId`를 비교하세요.





