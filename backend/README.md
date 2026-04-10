# 로그 관리 시스템 백엔드 API

## 개요
로그 관리 시스템의 백엔드 API 서버입니다.

## 기술 스택
- **Java**: 17
- **Build Tool**: Maven
- **Framework**: Spring Boot 3.1.5
- **Database**: PostgreSQL (향후 추가 예정)

## 프로젝트 구조
```
dev/backend/
├── src/
│   ├── main/
│   │   ├── java/com/logmng/
│   │   │   ├── config/          # 설정 클래스
│   │   │   ├── controller/       # REST 컨트롤러
│   │   │   ├── dto/             # 데이터 전송 객체
│   │   │   ├── exception/       # 예외 처리
│   │   │   └── LogManagementApplication.java
│   │   └── resources/
│   │       ├── application.yml   # 애플리케이션 설정
│   │       └── logback-spring.xml # 로깅 설정
│   └── test/
│       └── java/com/logmng/      # 테스트 코드
└── pom.xml                       # Maven 설정
```

## 빌드 및 실행

### 사전 요구사항
- Java 17 이상
- Maven 3.6 이상

### 빌드
```bash
mvn clean package
```

### 실행
```bash
mvn spring-boot:run
```

또는 빌드된 JAR 파일 실행:
```bash
java -jar target/logmng-backend-1.0.1.jar
```

### 테스트 실행
```bash
mvn test
```

## API 엔드포인트

### 헬스 체크
- **GET** `/api/health` - 서버 상태 확인

## 설정

### application.yml
주요 설정 항목:
- `server.port`: 서버 포트 (기본값: **9200** — 배포·계약과 동일)
- `logging.file.name`: 파일 로그 경로; 운영에서는 환경 변수 **`LOGGING_FILE_NAME`** 로 덮어쓰기 ([`docs/contract.md`](../docs/contract.md))
- `logging.level`: 로깅 레벨 설정
- `spring.jackson`: JSON 직렬화 설정

### 환경 변수·비밀
- **저장소 루트** [`.env.example`](../.env.example): 설치·런타임 키 템플릿(한글 주석). 실제 값은 **`.env`**에만 두고 `chmod 600`, **커밋하지 않음** (`.gitignore`).
- **JDBC·스키마·3분할 DB** 등 정식 이름: [`docs/contract.md`](../docs/contract.md).
- **DB 프로비저닝**: [`DB_SETUP_GUIDE.md`](DB_SETUP_GUIDE.md), `src/main/resources/db/setup.sh`.

## 로깅
- 콘솔 출력 및 파일 로깅 지원
- 기본 파일 로그: **`logs/application.log`** (`LOGGING_FILE_NAME` 미설정 시 `application.yml` 기본과 동일)
- `logback-spring.xml`에 에러 전용 파일 등이 있으면 해당 설정을 따름

## 개발 가이드

### 패키지 구조
- `controller`: REST API 엔드포인트 정의
- `dto`: 요청/응답 데이터 전송 객체
- `exception`: 커스텀 예외 및 전역 예외 처리
- `config`: Spring 설정 클래스

### 공통 응답 형식
모든 API는 `ApiResponse<T>` 형식을 사용합니다:
```json
{
  "success": true,
  "data": {},
  "message": "성공 메시지",
  "error": "에러 메시지 (실패 시)",
  "code": "에러 코드 (실패 시)"
}
```

### 예외 처리
- `CustomException`: 커스텀 예외 클래스
- `GlobalExceptionHandler`: 전역 예외 처리 핸들러

## 라이선스
내부 사용





