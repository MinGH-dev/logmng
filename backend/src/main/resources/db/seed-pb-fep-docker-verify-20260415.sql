-- Docker / manual verification seed for requirement:
--   docs/requirements/20260415-pb-fep-log-time-20-char-microseconds.md
--
-- Idempotent marker: reserve = 'DVFY' (VARCHAR(4)) on pb_send / pb_recv.
-- log_time: exactly 20 digits yyyyMMddHHmmssSSSSSS (microseconds).
--
-- Apply (example, from repo root; postgres-pb service, database pbfep):
--   docker compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . \
--     exec -T postgres-pb psql -U postgres -d pbfep -v ON_ERROR_STOP=1 \
--     -f /path/in/container/...   # or: psql ... < backend/src/main/resources/db/seed-pb-fep-docker-verify-20260415.sql
--
-- Prerequisites: daily RANGE partition exists for the calendar day of each log_time (setup.sh / partition scripts).

SET search_path TO public;

BEGIN;

DELETE FROM pb_send WHERE reserve = 'DVFY';
DELETE FROM pb_recv WHERE reserve = 'DVFY';

INSERT INTO pb_send (log_time, tr_code, brodid, media_gb, prc_time, con_key, data, reserve)
VALUES
    (
        '20260415143025123456',
        'DV',
        'dvfy_docker',
        '01',
        '143025000',
        'DVFY-SEND-01',
        'Docker verify PB FEP send (20-char log_time)',
        'DVFY'
    );

INSERT INTO pb_recv (log_time, tr_code, brodid, media_gb, prc_time, con_key, data, reserve)
VALUES
    (
        '20260415144500987654',
        'DV',
        'dvfy_docker',
        '01',
        '144500000',
        'DVFY-RECV-01',
        'Docker verify PB FEP recv (20-char log_time)',
        'DVFY'
    );

COMMIT;

-- Sanity (optional):
-- SELECT char_length(log_time), COUNT(*) FROM pb_send WHERE reserve='DVFY' GROUP BY 1;
-- SELECT char_length(log_time), COUNT(*) FROM pb_recv WHERE reserve='DVFY' GROUP BY 1;
