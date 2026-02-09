-- imagelog 테이블 샘플 데이터 삽입
-- status: input, output, error
-- datastring, headerstring: JSON string
-- 암호화된 데이터는 []로 감싸져 있음

-- 기존 데이터 삭제
DELETE FROM imagelog;

-- 샘플 데이터 삽입
INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time) VALUES
-- input 상태 샘플
('LDP', 'EduSG', 'SE10002_select', 'input', 
 'encrypted_data_1', 
 '{"id":"1110","name":"","age":0,"r":"","p":"[E002Jo1mpzWoGoFvutn6NhYlAeMXNh4nURpSji8S5xIqIyCiHQF9xw/cB7O4c6ebk337]"}',
 '250315142429291DAOLCS0TT0S01090000045001',
 'encrypted_header_1',
 '{"flag":"\u0000","inputMsgType":"JSON","outputMsgType":"JSON","guid":"250315142429291DAOLCS0TT0S01090000045001"}',
 EXTRACT(EPOCH FROM NOW() - INTERVAL '1 hour') * 1000),

-- input 상태 샘플 2
('LDP', 'EduSG', 'SE10003_insert', 'input',
 'encrypted_data_2',
 '{"id":"2220","name":"홍길동","age":30,"email":"[A1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6Q7R8S9T0U1V2W3X4Y5Z6]","phone":"010-1234-5678"}',
 '250315142429291DAOLCS0TT0S01090000045002',
 'encrypted_header_2',
 '{"flag":"\u0000","inputMsgType":"JSON","outputMsgType":"JSON","guid":"250315142429291DAOLCS0TT0S01090000045002","sessionId":"[S1E2S3S4I5O6N7I8D9A0T1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6]"}',
 EXTRACT(EPOCH FROM NOW() - INTERVAL '2 hours') * 1000),

-- output 상태 샘플
('LDP', 'EduSG', 'SE10002_select', 'output',
 'encrypted_data_3',
 '{"result":"success","data":[{"id":"1110","name":"김철수","age":25}],"count":1,"message":"[O1U2T3P4U5T6M7E8S9S0A1G2E3D4A5T6A7H8E9R0E1I2S3E4N5C6R7Y8P9T0E1D2]"}',
 '250315142429291DAOLCS0TT0S01090000045003',
 'encrypted_header_3',
 '{"flag":"\u0000","inputMsgType":"JSON","outputMsgType":"JSON","guid":"250315142429291DAOLCS0TT0S01090000045003","responseCode":"200"}',
 EXTRACT(EPOCH FROM NOW() - INTERVAL '30 minutes') * 1000),

-- output 상태 샘플 2
('LDP', 'EduSG', 'SE10003_insert', 'output',
 'encrypted_data_4',
 '{"result":"success","insertedId":"2220","message":"데이터가 성공적으로 저장되었습니다","timestamp":"[T1I2M3E4S5T6A7M8P9D0A1T2A3H4E5R6E7I8S9E0N1C2R3Y4P5T6E7D8]"}',
 '250315142429291DAOLCS0TT0S01090000045004',
 'encrypted_header_4',
 '{"flag":"\u0000","inputMsgType":"JSON","outputMsgType":"JSON","guid":"250315142429291DAOLCS0TT0S01090000045004","responseCode":"201"}',
 EXTRACT(EPOCH FROM NOW() - INTERVAL '1 hour 30 minutes') * 1000),

-- error 상태 샘플
('LDP', 'EduSG', 'SE10002_select', 'error',
 'encrypted_data_5',
 '{"error":"Database connection failed","code":"DB_ERROR","details":"[E1R2R3O4R5D6E7T8A9I0L1S2H3E4R5E6I7S8E9N0C1R2Y3P4T5E6D7D8A9T0A1H2E3R4E5I6S7E8N9C0R1Y2P3T4E5D6]","timestamp":"2025-01-15T14:24:29"}',
 '250315142429291DAOLCS0TT0S01090000045005',
 'encrypted_header_5',
 '{"flag":"\u0000","inputMsgType":"JSON","outputMsgType":"JSON","guid":"250315142429291DAOLCS0TT0S01090000045005","responseCode":"500","errorCode":"DB_CONNECTION_ERROR"}',
 EXTRACT(EPOCH FROM NOW() - INTERVAL '3 hours') * 1000),

-- error 상태 샘플 2
('LDP', 'EduSG', 'SE10004_update', 'error',
 'encrypted_data_6',
 '{"error":"Validation failed","code":"VALIDATION_ERROR","fields":["name","email"],"message":"[V1A2L3I4D5A6T7I8O9N0E1R2R3O4R5M6E7S8S9A0G1E2H3E4R5E6I7S8E9N0C1R2Y3P4T5E6D7D8A9T0A1H2E3R4E5I6S7E8N9C0R1Y2P3T4E5D6]"}',
 '250315142429291DAOLCS0TT0S01090000045006',
 'encrypted_header_6',
 '{"flag":"\u0000","inputMsgType":"JSON","outputMsgType":"JSON","guid":"250315142429291DAOLCS0TT0S01090000045006","responseCode":"400","errorCode":"VALIDATION_ERROR"}',
 EXTRACT(EPOCH FROM NOW() - INTERVAL '4 hours') * 1000),

-- 다른 application 샘플 (input)
('SYSTEM_B', 'Group1', 'SERVICE_001', 'input',
 'encrypted_data_7',
 '{"userId":"user123","action":"login","password":"[P1A2S3S4W5O6R7D8H9E0R1E2I3S4E5N6C7R8Y9P0T1E2D3D4A5T6A7H8E9R0E1I2S3E4N5C6R7Y8P9T0E1D2]"}',
 '250315142429291DAOLCS0TT0S01090000045007',
 'encrypted_header_7',
 '{"flag":"\u0000","inputMsgType":"JSON","outputMsgType":"JSON","guid":"250315142429291DAOLCS0TT0S01090000045007","ipAddress":"192.168.1.100"}',
 EXTRACT(EPOCH FROM NOW() - INTERVAL '5 hours') * 1000),

-- 다른 application 샘플 (output)
('SYSTEM_B', 'Group1', 'SERVICE_001', 'output',
 'encrypted_data_8',
 '{"result":"success","token":"[T1O2K3E4N5H6E7R8E9I0S1E2N3C4R5Y6P7T8E9D0D1A2T3A4H5E6R7E8I9S0E1N2C3R4Y5P6T7E8D9D0A1T2A3H4E5R6E7I8S9E0N1C2R3Y4P5T6E7D8]","expiresIn":3600}',
 '250315142429291DAOLCS0TT0S01090000045008',
 'encrypted_header_8',
 '{"flag":"\u0000","inputMsgType":"JSON","outputMsgType":"JSON","guid":"250315142429291DAOLCS0TT0S01090000045008","responseCode":"200"}',
 EXTRACT(EPOCH FROM NOW() - INTERVAL '6 hours') * 1000);
