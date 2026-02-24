-- 검색 이력 테이블 (복호화 승인 부가 기능) — 기존 DB에만 적용
CREATE TABLE IF NOT EXISTS search_history (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    log_type VARCHAR(50) NOT NULL,
    search_params TEXT NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_search_history_user_id ON search_history(user_id);
CREATE INDEX IF NOT EXISTS idx_search_history_requested_at ON search_history(requested_at);
CREATE INDEX IF NOT EXISTS idx_search_history_user_requested ON search_history(user_id, requested_at DESC);

GRANT ALL PRIVILEGES ON TABLE search_history TO logmng;
GRANT USAGE, SELECT ON SEQUENCE search_history_id_seq TO logmng;
