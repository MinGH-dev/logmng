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





