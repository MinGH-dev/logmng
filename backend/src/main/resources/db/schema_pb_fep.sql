-- PB FEP 로그 스키마 (송신/수신 테이블 — 레거시 PB FEP 와이어 포맨 정렬)
-- PostgreSQL 16
-- App PB FEP log search reads pb_send and pb_recv (UNION ALL); unqualified names require search_path to include SCHEMA_PB.
-- pb_send / pb_recv: 동일 와이어 컬럼 집합(UNION 호환). 비대칭이 생기면 요구사항 문서에 명시.
--
-- 시간 컬럼 관계 (요구사항 20260414-pb-fep-log-timestamp-physical-removal-bugfix, 20260415-pb-fep-log-time-20-char-microseconds):
--   * log_timestamp는 물리적으로 제거한다(적재 실패 원인 제거 목적).
--   * log_time — 제품 표준: lexical 20 digits yyyyMMddHHmmssSSSSSS (microseconds zero-padded). prc_time — 와이어 원문 VARCHAR.
--   * wire_ts — 레거시 DDL의 따옴표 "timestamp" 컬럼에 대응; PostgreSQL 예약어 회피용 이름.
--
-- 적용 시 세션 search_path에 SCHEMA_PB(예: logmng)가 앞에 오도록 setup.sh 또는 psql에서 설정.

-- 송신 로그 테이블
CREATE TABLE IF NOT EXISTS pb_send (
    id BIGSERIAL PRIMARY KEY,
    log_time VARCHAR(20),
    log_ch_cd VARCHAR(6),
    log_io_cd VARCHAR(1),
    log_len VARCHAR(6),
    len VARCHAR(6),
    tr_gb VARCHAR(1),
    comp_gb VARCHAR(1),
    enct_gb VARCHAR(1),
    data_off VARCHAR(3),
    tr_code VARCHAR(8),
    comp_no VARCHAR(3),
    brodid VARCHAR(16),
    media_gb VARCHAR(2),
    channel_no VARCHAR(3),
    tr_seq VARCHAR(9),
    trid_sign VARCHAR(1),
    trid_media_gb VARCHAR(2),
    trid_term_no VARCHAR(3),
    trid_svr_no VARCHAR(3),
    trid_svr_seq VARCHAR(7),
    pub_ip VARCHAR(12),
    prt_ip VARCHAR(12),
    prc_brno VARCHAR(3),
    brno VARCHAR(3),
    term_no VARCHAR(8),
    lan_gb VARCHAR(1),
    prc_time VARCHAR(9),
    msg_code VARCHAR(4),
    msg_gb VARCHAR(1),
    compress_re VARCHAR(1),
    fnc_key VARCHAR(4),
    rec_cnt VARCHAR(4),
    exp_prc VARCHAR(2),
    reserve VARCHAR(4),
    con_gb VARCHAR(1),
    con_key VARCHAR(18),
    vlen_len VARCHAR(2),
    vhd_len VARCHAR(2),
    bmsg_len VARCHAR(2),
    vlen TEXT,
    vhd TEXT,
    bmsg TEXT,
    data TEXT,
    wire_ts VARCHAR(19),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 수신 로그 테이블 (와이어 컬럼 집합 동일)
CREATE TABLE IF NOT EXISTS pb_recv (
    id BIGSERIAL PRIMARY KEY,
    log_time VARCHAR(20),
    log_ch_cd VARCHAR(6),
    log_io_cd VARCHAR(1),
    log_len VARCHAR(6),
    len VARCHAR(6),
    tr_gb VARCHAR(1),
    comp_gb VARCHAR(1),
    enct_gb VARCHAR(1),
    data_off VARCHAR(3),
    tr_code VARCHAR(8),
    comp_no VARCHAR(3),
    brodid VARCHAR(16),
    media_gb VARCHAR(2),
    channel_no VARCHAR(3),
    tr_seq VARCHAR(9),
    trid_sign VARCHAR(1),
    trid_media_gb VARCHAR(2),
    trid_term_no VARCHAR(3),
    trid_svr_no VARCHAR(3),
    trid_svr_seq VARCHAR(7),
    pub_ip VARCHAR(12),
    prt_ip VARCHAR(12),
    prc_brno VARCHAR(3),
    brno VARCHAR(3),
    term_no VARCHAR(8),
    lan_gb VARCHAR(1),
    prc_time VARCHAR(9),
    msg_code VARCHAR(4),
    msg_gb VARCHAR(1),
    compress_re VARCHAR(1),
    fnc_key VARCHAR(4),
    rec_cnt VARCHAR(4),
    exp_prc VARCHAR(2),
    reserve VARCHAR(4),
    con_gb VARCHAR(1),
    con_key VARCHAR(18),
    vlen_len VARCHAR(2),
    vhd_len VARCHAR(2),
    bmsg_len VARCHAR(2),
    vlen TEXT,
    vhd TEXT,
    bmsg TEXT,
    data TEXT,
    wire_ts VARCHAR(19),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pb_send_log_time ON pb_send(log_time);
CREATE INDEX IF NOT EXISTS idx_pb_send_brodid ON pb_send(brodid);
CREATE INDEX IF NOT EXISTS idx_pb_send_media_gb ON pb_send(media_gb);
CREATE INDEX IF NOT EXISTS idx_pb_send_tr_code ON pb_send(tr_code);

CREATE INDEX IF NOT EXISTS idx_pb_recv_log_time ON pb_recv(log_time);
CREATE INDEX IF NOT EXISTS idx_pb_recv_brodid ON pb_recv(brodid);
CREATE INDEX IF NOT EXISTS idx_pb_recv_media_gb ON pb_recv(media_gb);
CREATE INDEX IF NOT EXISTS idx_pb_recv_tr_code ON pb_recv(tr_code);

CREATE INDEX IF NOT EXISTS idx_pb_send_search ON pb_send(log_time, media_gb, tr_code);
CREATE INDEX IF NOT EXISTS idx_pb_recv_search ON pb_recv(log_time, media_gb, tr_code);

CREATE INDEX IF NOT EXISTS idx_pb_send_con_key ON pb_send(con_key);
CREATE INDEX IF NOT EXISTS idx_pb_recv_con_key ON pb_recv(con_key);

-- 업데이트 시간 자동 갱신 함수 (시스템 테이블·schema_user_activity_log에서도 사용)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_pb_send_updated_at ON pb_send;
CREATE TRIGGER update_pb_send_updated_at
    BEFORE UPDATE ON pb_send
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_pb_recv_updated_at ON pb_recv;
CREATE TRIGGER update_pb_recv_updated_at
    BEFORE UPDATE ON pb_recv
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
