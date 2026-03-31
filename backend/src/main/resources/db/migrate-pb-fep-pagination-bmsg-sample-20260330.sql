-- PB FEP v2 wireframe: pagination + bmsg (error_message) dev sample on DB A (SCHEMA_PB).
-- Wireframe maps bmsg <- error_message on pb_send / pb_recv (LogDbService.mapPbFepWireframe).
-- Target calendar day: 2025-10-10; login filter uses user_id (response login_id).
--
-- Idempotency: DELETE rows where session_id LIKE 'seed-pb-fep-pag-bmsg-%', then INSERT.
-- Re-running is safe. Legacy init-data rows (session001..003) are not deleted; they get
-- optional UPDATE of error_message for bmsg variety (same values each run).
--
-- Intended row counts after run (seeded rows only, per user on 2025-10-10):
--   user001, user002, user003: each 52 pb_send + 52 pb_recv = 104 rows (>= 100).
-- Plus existing init-data 1 send + 1 recv per user on that day (session001..003), unchanged except error_message.
--
-- Manual apply (replace schema and DB as needed):
--   psql -U postgres -d logmng -v ON_ERROR_STOP=1 \
--     -c "SET search_path TO logmng_sys, logmng, public;" \
--     -f migrate-pb-fep-pagination-bmsg-sample-20260330.sql
-- (Adjust search_path to match SCHEMA_SYS, SCHEMA_PB, public.)

DELETE FROM pb_send WHERE session_id LIKE 'seed-pb-fep-pag-bmsg-%';
DELETE FROM pb_recv WHERE session_id LIKE 'seed-pb-fep-pag-bmsg-%';

-- Legacy init-data rows (same day): varied error_message for short/medium/long wrap tests.
UPDATE pb_send AS p
SET error_message = v.msg
FROM (VALUES
  ('session001', '정상 처리'),
  ('session002', '처리 완료 (우회 경로 경유, 지연 340ms)'),
  ('session003', E'[연동 오류] 하위 시스템 거부\n코드: SUB-REFUSED\n원인: 인증서 만료 또는 방화벽 차단\n조치: 인프라 담당자에게 티켓 등록 후 재시도')
) AS v(sid, msg)
WHERE p.session_id = v.sid
  AND p.log_timestamp >= TIMESTAMP '2025-10-10'
  AND p.log_timestamp < TIMESTAMP '2025-10-11';

UPDATE pb_recv AS p
SET error_message = v.msg
FROM (VALUES
  ('session001', '정상 처리'),
  ('session002', E'경고: 응답 지연 감지\n잔여 SLA: 120ms'),
  ('session003',
   repeat('장문오류 ', 28) || E'\n' || repeat('—', 55) || E'\n' ||
   'trace=legacy-recv-session003' || E'\n' ||
   'detail: downstream reset by peer; multiline safe for UI wrap')
) AS v(sid, msg)
WHERE p.session_id = v.sid
  AND p.log_timestamp >= TIMESTAMP '2025-10-10'
  AND p.log_timestamp < TIMESTAMP '2025-10-11';

-- 52 send + 52 recv per user (user001, user002, user003) => 104 rows/user on 2025-10-10.
INSERT INTO pb_send (
  log_timestamp, media_code, tr_code, user_id, ip_address, user_agent,
  request_data, response_data, status_code, response_time, error_message, session_id, device_type
)
SELECT
  timestamp '2025-10-10 00:00:00' + (((u.user_ord * 30000) + (n * 137)) % 86400) * interval '1 second',
  (ARRAY['A', 'B', 'C'])[1 + (n % 3)],
  'S' || (ARRAY['A', 'B', 'C'])[1 + (n % 3)] || 'T' || lpad(n::text, 4, '0'),
  u.uid,
  '192.168.' || (1 + u.user_ord)::text || '.' || (100 + (n % 55))::text,
  'Mozilla/5.0 PB-FEP seed',
  '{"seed":"pb-fep-pag-bmsg","dir":"send","n":' || n || '}',
  '{"result":"ok"}',
  CASE WHEN (u.user_ord + n) % 17 = 0 THEN 503 ELSE 200 END,
  30 + (n % 400),
  CASE ((u.user_ord * 31 + n * 7) % 6)
    WHEN 0 THEN '정상 처리'
    WHEN 1 THEN '정상 처리 완료'
    WHEN 2 THEN '지연: 평균 대기열 길이 초과 후 재시도 성공'
    WHEN 3 THEN '[HTTP-502] Bad Gateway — upstream unavailable'
    WHEN 4 THEN E'스키마 검증 실패\n필드: tr_code\n기대 패턴: S[A-C]T####'
    ELSE repeat('오류요약 ', 18) || E'\n' || repeat('—', 48) || E'\n' || 'trace=' || u.uid || '-s-' || n::text
  END,
  'seed-pb-fep-pag-bmsg-' || u.uid || '-s-' || lpad(n::text, 3, '0'),
  CASE WHEN n % 2 = 0 THEN 'PC' ELSE 'Mobile' END
FROM (VALUES
  (0, 'user001'),
  (1, 'user002'),
  (2, 'user003')
) AS u(user_ord, uid)
CROSS JOIN generate_series(1, 52) AS n;

INSERT INTO pb_recv (
  log_timestamp, media_code, tr_code, user_id, ip_address, user_agent,
  request_data, response_data, status_code, response_time, error_message, session_id, device_type
)
SELECT
  timestamp '2025-10-10 00:00:05' + (((u.user_ord * 30000) + (n * 139)) % 86390) * interval '1 second',
  (ARRAY['A', 'B', 'C'])[1 + ((n + 1) % 3)],
  'R' || (ARRAY['A', 'B', 'C'])[1 + ((n + 1) % 3)] || 'T' || lpad(n::text, 4, '0'),
  u.uid,
  '10.0.' || (1 + u.user_ord)::text || '.' || (200 + (n % 54))::text,
  'Mozilla/5.0 PB-FEP seed recv',
  '{"seed":"pb-fep-pag-bmsg","dir":"recv","n":' || n || '}',
  '{"ack":true}',
  CASE WHEN (u.user_ord * 3 + n) % 19 = 0 THEN 500 ELSE 200 END,
  25 + (n % 350),
  CASE ((u.user_ord * 29 + n * 11) % 6)
    WHEN 0 THEN '정상 처리'
    WHEN 1 THEN '수신 확인 완료'
    WHEN 2 THEN '부분 성공: 일부 필드 기본값 적용됨'
    WHEN 3 THEN '[ERR-504] 게이트웨이 타임아웃 — 상위 시스템 무응답'
    WHEN 4 THEN E'파싱 오류\n위치: offset 128\n토큰: unexpected EOF'
    ELSE repeat('다줄 ', 22) || E'\n' || 'line2: 반복 문자열로 줄바꿈 래핑 검증' || E'\n' ||
         repeat('=', 40) || E'\n' || 'footer: ' || u.uid || '-r-' || n::text
  END,
  'seed-pb-fep-pag-bmsg-' || u.uid || '-r-' || lpad(n::text, 3, '0'),
  CASE WHEN n % 3 = 0 THEN 'Tablet' WHEN n % 2 = 0 THEN 'PC' ELSE 'Mobile' END
FROM (VALUES
  (0, 'user001'),
  (1, 'user002'),
  (2, 'user003')
) AS u(user_ord, uid)
CROSS JOIN generate_series(1, 52) AS n;
