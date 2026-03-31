-- Req: companion rows for historical imagelog rows where a guid has exactly one
-- distinct normalized status (per uq_imagelog_guid_row_status): input-only → add output;
-- error-only → add input; output-only → add input.
-- Idempotent: INSERT ... SELECT ... WHERE NOT EXISTS matching (guid, normalized target status).
-- JSON: plain JSON in datastring/headerstring; phase request/response/requestHeader/responseHeader
-- aligned with migrate-imagelog-dup-guid-sample-20260330.sql; syntheticCompanion marks generated rows.
-- Payload mirrors source text via to_jsonb(...) to avoid jsonb cast failures (e.g. \\u0000 in legacy samples).
-- Apply with: SET search_path TO SCHEMA_IMAGELOG (e.g. public).

-- 1) Guid has only "input" → insert one "output" companion
INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
SELECT
  s.application,
  s.servicegroup,
  s.service,
  'output',
  COALESCE(s.data, ''),
  (
    jsonb_build_object(
      'phase', 'response',
      'syntheticCompanion', true,
      'guid', s.guid,
      'mirroredFromStatus', 'input'
    )
    || CASE
      WHEN NULLIF(BTRIM(COALESCE(s.datastring, '')), '') IS NOT NULL THEN
        jsonb_build_object('payload', to_jsonb(LEFT(BTRIM(s.datastring), 12000)))
      ELSE
        '{}'::jsonb
    END
  )::text,
  s.guid,
  COALESCE(s.header, ''),
  (
    jsonb_build_object(
      'phase', 'responseHeader',
      'syntheticCompanion', true,
      'guid', s.guid
    )
    || CASE
      WHEN NULLIF(BTRIM(COALESCE(s.headerstring, '')), '') IS NOT NULL THEN
        jsonb_build_object('payload', to_jsonb(LEFT(BTRIM(s.headerstring), 12000)))
      ELSE
        '{}'::jsonb
    END
  )::text,
  s.insert_time
FROM imagelog s
INNER JOIN (
  SELECT guid
  FROM imagelog
  GROUP BY guid
  HAVING COUNT(DISTINCT COALESCE(NULLIF(TRIM(status), ''), '')) = 1
    AND MAX(LOWER(COALESCE(NULLIF(TRIM(status), ''), ''))) = 'input'
) g ON g.guid = s.guid
WHERE LOWER(COALESCE(NULLIF(TRIM(s.status), ''), '')) = 'input'
  AND NOT EXISTS (
    SELECT 1
    FROM imagelog x
    WHERE x.guid = s.guid
      AND COALESCE(NULLIF(TRIM(x.status), ''), '') = COALESCE(NULLIF(TRIM('output'), ''), '')
  );

-- 2) Guid has only "error" → insert one "input" companion
INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
SELECT
  s.application,
  s.servicegroup,
  s.service,
  'input',
  COALESCE(s.data, ''),
  (
    jsonb_build_object(
      'phase', 'request',
      'syntheticCompanion', true,
      'guid', s.guid,
      'mirroredFromStatus', 'error'
    )
    || CASE
      WHEN NULLIF(BTRIM(COALESCE(s.datastring, '')), '') IS NOT NULL THEN
        jsonb_build_object('payload', to_jsonb(LEFT(BTRIM(s.datastring), 12000)))
      ELSE
        '{}'::jsonb
    END
  )::text,
  s.guid,
  COALESCE(s.header, ''),
  (
    jsonb_build_object(
      'phase', 'requestHeader',
      'syntheticCompanion', true,
      'guid', s.guid
    )
    || CASE
      WHEN NULLIF(BTRIM(COALESCE(s.headerstring, '')), '') IS NOT NULL THEN
        jsonb_build_object('payload', to_jsonb(LEFT(BTRIM(s.headerstring), 12000)))
      ELSE
        '{}'::jsonb
    END
  )::text,
  s.insert_time
FROM imagelog s
INNER JOIN (
  SELECT guid
  FROM imagelog
  GROUP BY guid
  HAVING COUNT(DISTINCT COALESCE(NULLIF(TRIM(status), ''), '')) = 1
    AND MAX(LOWER(COALESCE(NULLIF(TRIM(status), ''), ''))) = 'error'
) g ON g.guid = s.guid
WHERE LOWER(COALESCE(NULLIF(TRIM(s.status), ''), '')) = 'error'
  AND NOT EXISTS (
    SELECT 1
    FROM imagelog x
    WHERE x.guid = s.guid
      AND COALESCE(NULLIF(TRIM(x.status), ''), '') = COALESCE(NULLIF(TRIM('input'), ''), '')
  );

-- 3) Guid has only "output" → insert one "input" companion
INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
SELECT
  s.application,
  s.servicegroup,
  s.service,
  'input',
  COALESCE(s.data, ''),
  (
    jsonb_build_object(
      'phase', 'request',
      'syntheticCompanion', true,
      'guid', s.guid,
      'mirroredFromStatus', 'output'
    )
    || CASE
      WHEN NULLIF(BTRIM(COALESCE(s.datastring, '')), '') IS NOT NULL THEN
        jsonb_build_object('payload', to_jsonb(LEFT(BTRIM(s.datastring), 12000)))
      ELSE
        '{}'::jsonb
    END
  )::text,
  s.guid,
  COALESCE(s.header, ''),
  (
    jsonb_build_object(
      'phase', 'requestHeader',
      'syntheticCompanion', true,
      'guid', s.guid
    )
    || CASE
      WHEN NULLIF(BTRIM(COALESCE(s.headerstring, '')), '') IS NOT NULL THEN
        jsonb_build_object('payload', to_jsonb(LEFT(BTRIM(s.headerstring), 12000)))
      ELSE
        '{}'::jsonb
    END
  )::text,
  s.insert_time
FROM imagelog s
INNER JOIN (
  SELECT guid
  FROM imagelog
  GROUP BY guid
  HAVING COUNT(DISTINCT COALESCE(NULLIF(TRIM(status), ''), '')) = 1
    AND MAX(LOWER(COALESCE(NULLIF(TRIM(status), ''), ''))) = 'output'
) g ON g.guid = s.guid
WHERE LOWER(COALESCE(NULLIF(TRIM(s.status), ''), '')) = 'output'
  AND NOT EXISTS (
    SELECT 1
    FROM imagelog x
    WHERE x.guid = s.guid
      AND COALESCE(NULLIF(TRIM(x.status), ''), '') = COALESCE(NULLIF(TRIM('input'), ''), '')
  );
