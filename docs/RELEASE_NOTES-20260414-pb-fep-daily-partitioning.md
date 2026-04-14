# 릴리스 노트 — 1.0.2 (2026-04-14)

## 개요

PB FEP 로그 테이블(`pb_send`, `pb_recv`) 파티셔닝을 **일 단위**로 정렬하고, 배포 산출물 버전을 **1.0.2**로 올렸습니다.

## 주요 변경

### 데이터베이스 (PB FEP)

- `log_timestamp` RANGE 파티션을 **캘린더 일 단위**로 생성·관리하도록 마이그레이션·설정 스크립트를 갱신했습니다.
- 신규/갱신: `migrate-pb-send-recv-partitioning-20260408.sql`
- 월 단위 → 일 단위 이전용: `migrate-pb-send-recv-monthly-to-daily-20260414.sql`
- 연동: `setup.sh`, `check-db.sh`, `backend/DB_SETUP_GUIDE.md`

요구사항 문서: `docs/requirements/20260414-pb-fep-daily-partitioning.md`

### 배포·번들

- 애플리케이션·오프라인 번들 기본 버전 **1.0.2** (`CHANGELOG.md` [1.0.2] 참고).
