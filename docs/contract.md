# 공통 계약 (Contract)

프론트엔드·백엔드·DB 작업 시 **이 문서와 참조 스펙을 기준**으로 한다. API·스키마 변경 시 여기에 맞추거나, 변경 시 이 문서/스펙을 먼저 갱신한다.

## 환경·포트 (단일 진실)

| 구분 | 값 | 설정 위치 |
|------|-----|-----------|
| 백엔드 API | http://localhost:9200/api | backend application.yml, frontend .env REACT_APP_API_BASE_URL |
| 프론트엔드 | http://localhost:3001 | frontend/.env PORT |
| DB | localhost:5432, DB logmng | backend application.yml datasource |

## API 규격

- **정의 위치**: `docs/api-definition.md`(현재 구현 API 목록·요청/응답), `specs/*.spec.yaml` 또는 기능별 요건 문서의 API 섹션. 새 API/변경 시 해당 스펙을 먼저 작성·수정한다.
- **구현**: 백엔드는 스펙에 정의된 경로·메서드·요청/응답 형식을 따른다. 프론트엔드는 동일 스펙을 참고해 호출한다.
- **공통 베이스**: `/api` (백엔드 context-path 아님 경우 application.yml 기준).
- **API 정의서**: [docs/api-definition.md](api-definition.md) — 인증, 헬스, 로그 타입, DB 로그 검색/상세/복호화, 검색 추천, 검색 이력(승인 대기·승인·반려 포함), 사용자 관리(결재자 지정), 활동 이력, DB 테스트 등 구현된 API 전부. 복호화 결재자 관련 API는 api-definition §6.1.5·6.1.6·6.1.7 및 §7 참고.

## DB 스키마

- **정의 위치**: `backend/src/main/resources/db/schema.sql` 및 필요 시 `specs/` 내 스키마 기술.
- **변경**: 스키마 변경 시 schema.sql(또는 마이그레이션)을 먼저 반영하고, 백엔드 코드·API 스펙을 그에 맞춘다.

## 크로스 레이어 작업 시

- API 추가/변경 → `specs/` 또는 docs/contract.md 참조 스펙을 먼저 갱신한 뒤, 백엔드·프론트 각각 구현.
- DB 변경 → schema.sql(또는 스펙) 반영 후 백엔드·API 스펙 정합성 유지.

이 문서는 dev 워크스페이스 전용이다. 변경 시 docs/README.md 등과 맞춘다.
