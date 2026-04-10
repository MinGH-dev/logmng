-- 이미지로그 테이블 스키마
-- PostgreSQL 16

-- 이미지로그 테이블
CREATE TABLE IF NOT EXISTS imagelog (
    application VARCHAR(256),
    servicegroup VARCHAR(256),
    service VARCHAR(256),
    status VARCHAR(256),
    data TEXT,
    datastring TEXT,
    guid VARCHAR(256),
    header TEXT,
    headerstring TEXT,
    insert_time BIGINT
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_imagelog_insert_time ON imagelog(insert_time);
CREATE INDEX IF NOT EXISTS idx_imagelog_application ON imagelog(application);
CREATE INDEX IF NOT EXISTS idx_imagelog_servicegroup ON imagelog(servicegroup);
CREATE INDEX IF NOT EXISTS idx_imagelog_service ON imagelog(service);
CREATE INDEX IF NOT EXISTS idx_imagelog_status ON imagelog(status);
CREATE INDEX IF NOT EXISTS idx_imagelog_guid ON imagelog(guid);

-- Req 20260320: business uniqueness (guid, status); coalesce for null/blank status
CREATE UNIQUE INDEX IF NOT EXISTS uq_imagelog_guid_row_status ON imagelog (guid, COALESCE(NULLIF(TRIM(status), ''), ''));

-- 복합 인덱스 (검색 성능 향상)
CREATE INDEX IF NOT EXISTS idx_imagelog_search ON imagelog(insert_time, application, servicegroup, service);

-- 권한 부여
GRANT ALL PRIVILEGES ON TABLE imagelog TO logmng;





