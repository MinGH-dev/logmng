INSERT INTO pb_send (id, log_timestamp, tr_code, user_id, ip_address, request_data, response_data,
    status_code, error_message, session_id, device_type)
VALUES (?, ?, 'TRX', 'userA', '10.0.0.1', 'reqBody', 'resBody', 42, 'err-hint', 'sess-1', 'WEB')
