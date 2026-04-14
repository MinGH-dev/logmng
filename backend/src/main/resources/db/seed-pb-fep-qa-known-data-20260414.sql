-- QA unblock seed for requirement:
--  - docs/requirements/20260414-pb-fep-log-timestamp-physical-removal-bugfix.md
--  - docs/requirements/20260414-pb-fep-log-timestamp-physical-removal-bugfix-1.md
--
-- Deterministic one-day condition:
--   date window: 2026-04-14 00:00:00 ~ 2026-04-14 23:59:59
--   brodid(loginId): qa_log_time
--   tr_code: QA
--
-- Notes:
-- - Parents must already be daily partitioned by log_time with no DEFAULT partition.
-- - This script is idempotent for this probe key/day: it deletes then re-inserts.

SET search_path TO public, public;

BEGIN;

DELETE FROM pb_send
WHERE log_time >= '20260414000000'
  AND log_time <= '20260414235959'
  AND brodid = 'qa_log_time'
  AND tr_code = 'QA';

DELETE FROM pb_recv
WHERE log_time >= '20260414000000'
  AND log_time <= '20260414235959'
  AND brodid = 'qa_log_time'
  AND tr_code = 'QA';

INSERT INTO pb_send (log_time, tr_code, brodid, media_gb, prc_time, con_key, data)
VALUES
('20260414091011', 'QA', 'qa_log_time', '01', '091011000', 'QA-SEND-001', 'QA deterministic send row 1'),
('20260414123456', 'QA', 'qa_log_time', '01', '123456000', 'QA-SEND-002', 'QA deterministic send row 2'),
('20260414182030', 'QA', 'qa_log_time', '01', '182030000', 'QA-SEND-003', 'QA deterministic send row 3');

INSERT INTO pb_recv (log_time, tr_code, brodid, media_gb, prc_time, con_key, data)
VALUES
('20260414102233', 'QA', 'qa_log_time', '01', '102233000', 'QA-RECV-001', 'QA deterministic recv row 1'),
('20260414154500', 'QA', 'qa_log_time', '01', '154500000', 'QA-RECV-002', 'QA deterministic recv row 2');

COMMIT;

-- Validation snippets:
-- SELECT COUNT(*) FROM pb_send
--  WHERE log_time >= '20260414000000' AND log_time <= '20260414235959'
--    AND brodid='qa_log_time' AND tr_code='QA';
-- SELECT COUNT(*) FROM pb_recv
--  WHERE log_time >= '20260414000000' AND log_time <= '20260414235959'
--    AND brodid='qa_log_time' AND tr_code='QA';
-- SELECT COUNT(*) FROM (
--   SELECT 1 FROM pb_send WHERE log_time >= '20260414000000' AND log_time <= '20260414235959' AND brodid='qa_log_time' AND tr_code='QA'
--   UNION ALL
--   SELECT 1 FROM pb_recv WHERE log_time >= '20260414000000' AND log_time <= '20260414235959' AND brodid='qa_log_time' AND tr_code='QA'
-- ) t;
