-- PB FEP v2 wireframe: pagination + bmsg dev sample on DB A (SCHEMA_PB).
-- Wireframe maps bmsg from row payload (LogDbService.mapPbFepWireframe); physical column is bmsg.
-- Target calendar day: 2025-10-10; login filter uses brodid (response login_id).
--
-- Idempotency: DELETE rows where data JSON contains seed pb-fep-pag-bmsg, then INSERT.
-- Re-running is safe. Legacy init-data rows (session001..003 in con_key) get optional UPDATE of bmsg.
--
-- Intended row counts after run (seeded rows only, per user on 2025-10-10):
--   user001, user002, user003: each 52 pb_send + 52 pb_recv = 104 rows (>= 100).
-- Plus existing init-data 1 send + 1 recv per user on that day (con_key session*), unchanged except bmsg.
--
-- Manual apply (replace schema and DB as needed):
--   psql -U postgres -d logmng -v ON_ERROR_STOP=1 \
--     -c "SET search_path TO logmng_sys, logmng, public;" \
--     -f migrate-pb-fep-pagination-bmsg-sample-20260330.sql
-- (Adjust search_path to match SCHEMA_SYS, SCHEMA_PB, public.)

DELETE FROM pb_send WHERE data IS NOT NULL AND data::text LIKE '%"seed":"pb-fep-pag-bmsg"%';
DELETE FROM pb_recv WHERE data IS NOT NULL AND data::text LIKE '%"seed":"pb-fep-pag-bmsg"%';

-- Legacy init-data rows (same day): varied bmsg for short/medium/long wrap tests.
UPDATE pb_send AS p
SET bmsg = v.msg
FROM (VALUES
  ('user001', 'SAAAA100', '정상 처리'),
  ('user002', 'SBBBB100', '처리 완료 (우회 경로 경유, 지연 340ms)'),
  ('user003', 'SCCCC100', E'[연동 오류] 하위 시스템 거부\n코드: SUB-REFUSED\n원인: 인증서 만료 또는 방화벽 차단\n조치: 인프라 담당자에게 티켓 등록 후 재시도')
) AS v(uid, trc, msg)
WHERE p.brodid = v.uid
  AND p.tr_code = v.trc
  AND p.log_time >= '20251010000000000000'
  AND p.log_time < '20251011000000000000';

UPDATE pb_recv AS p
SET bmsg = v.msg
FROM (VALUES
  ('user001', 'RAAAA100', '정상 처리'),
  ('user002', 'RBBBB100', E'경고: 응답 지연 감지\n잔여 SLA: 120ms'),
  ('user003', 'RCCCC100',
   repeat('장문오류 ', 28) || E'\n' || repeat('—', 55) || E'\n' ||
   'trace=legacy-recv-session003' || E'\n' ||
   'detail: downstream reset by peer; multiline safe for UI wrap')
) AS v(uid, trc, msg)
WHERE p.brodid = v.uid
  AND p.tr_code = v.trc
  AND p.log_time >= '20251010000000000000'
  AND p.log_time < '20251011000000000000';

-- 52 send + 52 recv per user (user001, user002, user003) => 104 rows/user on 2025-10-10.
INSERT INTO pb_send (
  log_time, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
  msg_code, data, bmsg, tr_seq, wire_ts
)
SELECT
  to_char(
    timestamp '2025-10-10 00:00:00' + (((u.user_ord * 30000) + (n * 137)) % 86400) * interval '1 second',
    'YYYYMMDDHH24MISS'
  ) || '000000',
  (ARRAY['01', '02', '03'])[1 + (n % 3)],
  'S' || (ARRAY['A', 'B', 'C'])[1 + (n % 3)] || 'T' || lpad(n::text, 4, '0'),
  u.uid,
  ('127.0.' || (1 + u.user_ord)::text || '.' || lpad(((n * 3) % 200)::text, 3, '0')),
  ('127.0.' || (2 + u.user_ord)::text || '.' || lpad(((n * 5) % 200)::text, 3, '0')),
  CASE WHEN n % 2 = 0 THEN 'PC    ' ELSE 'MOB   ' END,
  CASE WHEN (u.user_ord + n) % 17 = 0 THEN '0503' ELSE '0200' END,
  '{"seed":"pb-fep-pag-bmsg","dir":"send","n":' || n || '}',
  CASE ((u.user_ord * 31 + n * 7) % 6)
    WHEN 0 THEN '정상 처리'
    WHEN 1 THEN '정상 처리 완료'
    WHEN 2 THEN '지연: 평균 대기열 길이 초과 후 재시도 성공'
    WHEN 3 THEN '[HTTP-502] Bad Gateway — upstream unavailable'
    WHEN 4 THEN E'스키마 검증 실패\n필드: tr_code\n기대 패턴: S[A-C]T####'
    ELSE repeat('오류요약 ', 18) || E'\n' || repeat('—', 48) || E'\n' || 'trace=' || u.uid || '-s-' || n::text
  END,
  lpad(n::text, 9, '0'),
  to_char(
    timestamp '2025-10-10 00:00:00' + (((u.user_ord * 30000) + (n * 137)) % 86400) * interval '1 second',
    'YYYY-MM-DD HH24:MI:SS'
  )
FROM (VALUES
  (0, 'user001'),
  (1, 'user002'),
  (2, 'user003')
) AS u(user_ord, uid)
CROSS JOIN generate_series(1, 52) AS n;

INSERT INTO pb_recv (
  log_time, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
  msg_code, data, bmsg, tr_seq, wire_ts
)
SELECT
  to_char(
    timestamp '2025-10-10 00:00:05' + (((u.user_ord * 30000) + (n * 139)) % 86390) * interval '1 second',
    'YYYYMMDDHH24MISS'
  ) || '000000',
  (ARRAY['01', '02', '03'])[1 + ((n + 1) % 3)],
  'R' || (ARRAY['A', 'B', 'C'])[1 + ((n + 1) % 3)] || 'T' || lpad(n::text, 4, '0'),
  u.uid,
  ('127.1.' || (1 + u.user_ord)::text || '.' || lpad(((n * 3) % 200)::text, 3, '0')),
  ('127.1.' || (2 + u.user_ord)::text || '.' || lpad(((n * 5) % 200)::text, 3, '0')),
  CASE WHEN n % 3 = 0 THEN 'TAB   ' WHEN n % 2 = 0 THEN 'PC    ' ELSE 'MOB   ' END,
  CASE WHEN (u.user_ord * 3 + n) % 19 = 0 THEN '0500' ELSE '0200' END,
  '{"seed":"pb-fep-pag-bmsg","dir":"recv","n":' || n || '}',
  CASE ((u.user_ord * 29 + n * 11) % 6)
    WHEN 0 THEN '정상 처리'
    WHEN 1 THEN '수신 확인 완료'
    WHEN 2 THEN '부분 성공: 일부 필드 기본값 적용됨'
    WHEN 3 THEN '[ERR-504] 게이트웨이 타임아웃 — 상위 시스템 무응답'
    WHEN 4 THEN E'파싱 오류\n위치: offset 128\n토큰: unexpected EOF'
    ELSE repeat('다줄 ', 22) || E'\n' || 'line2: 반복 문자열로 줄바꿈 래핑 검증' || E'\n' ||
         repeat('=', 40) || E'\n' || 'footer: ' || u.uid || '-r-' || n::text
  END,
  lpad(n::text, 9, '0'),
  to_char(
    timestamp '2025-10-10 00:00:05' + (((u.user_ord * 30000) + (n * 139)) % 86390) * interval '1 second',
    'YYYY-MM-DD HH24:MI:SS'
  )
FROM (VALUES
  (0, 'user001'),
  (1, 'user002'),
  (2, 'user003')
) AS u(user_ord, uid)
CROSS JOIN generate_series(1, 52) AS n;
