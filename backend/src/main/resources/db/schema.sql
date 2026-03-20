-- 로그 관리 시스템 데이터베이스 스키마 (집계 진입점)
-- PostgreSQL 16
--
-- 단일 DB·기본 public: psql -f schema.sql 실행 시 pb_fep → sys 순으로 로드되며,
-- 세션 기본 search_path가 public이면 기존과 동일하게 public에 객체가 생성됩니다.
--
-- 멀티 스키마/멀티 DB: setup.sh 및 backend/DB_SETUP_GUIDE.md 참고 (search_path·DB_B 등).

\i schema_pb_fep.sql
\i schema_sys.sql
