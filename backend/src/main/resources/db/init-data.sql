-- 초기 샘플 데이터 삽입

-- 송신 로그 샘플 데이터
INSERT INTO pb_send (log_timestamp, media_code, tr_code, user_id, ip_address, user_agent, request_data, response_data, status_code, response_time, session_id, device_type)
VALUES
    ('2025-10-10 10:00:00', 'A', 'SAAAA100', 'user001', '192.168.1.100', 'Mozilla/5.0', 'encrypted_request_data_1', 'encrypted_response_data_1', 200, 150, 'session001', 'PC'),
    ('2025-10-10 10:05:00', 'B', 'SBBBB100', 'user002', '192.168.1.101', 'Mozilla/5.0', 'encrypted_request_data_2', 'encrypted_response_data_2', 200, 120, 'session002', 'Mobile'),
    ('2025-10-10 10:10:00', 'C', 'SCCCC100', 'user003', '192.168.1.102', 'Mozilla/5.0', 'encrypted_request_data_3', 'encrypted_response_data_3', 200, 180, 'session003', 'PC');

-- 수신 로그 샘플 데이터
INSERT INTO pb_recv (log_timestamp, media_code, tr_code, user_id, ip_address, user_agent, request_data, response_data, status_code, response_time, session_id, device_type)
VALUES
    ('2025-10-10 10:01:00', 'A', 'RAAAA100', 'user001', '192.168.1.100', 'Mozilla/5.0', 'encrypted_request_data_1', 'encrypted_response_data_1', 200, 100, 'session001', 'PC'),
    ('2025-10-10 10:06:00', 'B', 'RBBBB100', 'user002', '192.168.1.101', 'Mozilla/5.0', 'encrypted_request_data_2', 'encrypted_response_data_2', 200, 110, 'session002', 'Mobile'),
    ('2025-10-10 10:11:00', 'C', 'RCCCC100', 'user003', '192.168.1.102', 'Mozilla/5.0', 'encrypted_request_data_3', 'encrypted_response_data_3', 200, 130, 'session003', 'PC');





