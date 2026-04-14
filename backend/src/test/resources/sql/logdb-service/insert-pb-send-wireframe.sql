INSERT INTO pb_send (id, log_time, tr_code, brodid, pub_ip, vlen, data,
    msg_code, bmsg, prt_ip, log_ch_cd)
VALUES (?, ?, 'TRX', 'userA', '10.0.0.1', 'reqBody', 'resBody',
    '42', 'err-hint', 'sess-1', 'WEB')
