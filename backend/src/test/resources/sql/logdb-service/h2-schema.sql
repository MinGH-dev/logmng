-- H2 fixtures for LogDbServiceTest (PostgreSQL compatibility mode)
-- pb_send / pb_recv: wire-aligned subset — req 20260414-pb-fep-wire-schema-alignment
CREATE TABLE IF NOT EXISTS imagelog (
    application VARCHAR(256), servicegroup VARCHAR(256), service VARCHAR(256), status VARCHAR(256),
    data TEXT, datastring TEXT, guid VARCHAR(256), header TEXT, headerstring TEXT, insert_time BIGINT
);
CREATE TABLE IF NOT EXISTS pb_send (
    id BIGINT PRIMARY KEY,
    log_time VARCHAR(15) NOT NULL,
    log_ch_cd VARCHAR(6),
    log_io_cd VARCHAR(1),
    tr_code VARCHAR(8),
    brodid VARCHAR(16),
    media_gb VARCHAR(2),
    pub_ip VARCHAR(12),
    prt_ip VARCHAR(12),
    prc_time VARCHAR(9),
    msg_code VARCHAR(4),
    term_no VARCHAR(8),
    vlen CLOB,
    vhd CLOB,
    bmsg CLOB,
    data CLOB,
    wire_ts VARCHAR(19),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS pb_recv (
    id BIGINT PRIMARY KEY,
    log_time VARCHAR(15) NOT NULL,
    log_ch_cd VARCHAR(6),
    log_io_cd VARCHAR(1),
    tr_code VARCHAR(8),
    brodid VARCHAR(16),
    media_gb VARCHAR(2),
    pub_ip VARCHAR(12),
    prt_ip VARCHAR(12),
    prc_time VARCHAR(9),
    msg_code VARCHAR(4),
    term_no VARCHAR(8),
    vlen CLOB,
    vhd CLOB,
    bmsg CLOB,
    data CLOB,
    wire_ts VARCHAR(19),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
