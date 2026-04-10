-- H2 fixtures for LogDbServiceTest (PostgreSQL compatibility mode)
CREATE TABLE IF NOT EXISTS imagelog (
    application VARCHAR(256), servicegroup VARCHAR(256), service VARCHAR(256), status VARCHAR(256),
    data TEXT, datastring TEXT, guid VARCHAR(256), header TEXT, headerstring TEXT, insert_time BIGINT
);
CREATE TABLE IF NOT EXISTS pb_send (
    id BIGINT PRIMARY KEY, log_timestamp TIMESTAMP, media_code VARCHAR(50), tr_code VARCHAR(50),
    user_id VARCHAR(100), ip_address VARCHAR(50), user_agent VARCHAR(500), request_data CLOB, response_data CLOB,
    status_code INT, response_time BIGINT, error_message CLOB, session_id VARCHAR(200), device_type VARCHAR(50),
    created_at TIMESTAMP, updated_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS pb_recv (
    id BIGINT PRIMARY KEY, log_timestamp TIMESTAMP, media_code VARCHAR(50), tr_code VARCHAR(50),
    user_id VARCHAR(100), ip_address VARCHAR(50), user_agent VARCHAR(500), request_data CLOB, response_data CLOB,
    status_code INT, response_time BIGINT, error_message CLOB, session_id VARCHAR(200), device_type VARCHAR(50),
    created_at TIMESTAMP, updated_at TIMESTAMP
);
