-- Req: docs/requirements/20260330-imagelog-dup-guid-sample-data.md
-- ImageLog DB / schema only. Apply with search_path set to SCHEMA_IMAGELOG (e.g. public).
-- Idempotent: inserts the duplicate-guid pair only when each (guid, normalized status) is absent
-- (same normalization as uq_imagelog_guid_row_status).

INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
SELECT 'LDP', 'EduSG', 'SE10002_select', 'input', '', '{"dupGuid":"GUID-DUP-PRETTY-20260330","phase":"request","pretty":true}', 'GUID-DUP-PRETTY-20260330', '', '{"dupGuid":"GUID-DUP-PRETTY-20260330","phase":"requestHeader","pretty":true}', EXTRACT(EPOCH FROM NOW() - INTERVAL '78 hours') * 1000
WHERE NOT EXISTS (
  SELECT 1 FROM imagelog i
  WHERE i.guid = 'GUID-DUP-PRETTY-20260330'
    AND COALESCE(NULLIF(TRIM(i.status), ''), '') = COALESCE(NULLIF(TRIM('input'), ''), '')
);

INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
SELECT 'LDP', 'EduSG', 'SE10002_select', 'output', '', '{"dupGuid":"GUID-DUP-PRETTY-20260330","phase":"response","pretty":true}', 'GUID-DUP-PRETTY-20260330', '', '{"dupGuid":"GUID-DUP-PRETTY-20260330","phase":"responseHeader","pretty":true}', EXTRACT(EPOCH FROM NOW() - INTERVAL '79 hours') * 1000
WHERE NOT EXISTS (
  SELECT 1 FROM imagelog i
  WHERE i.guid = 'GUID-DUP-PRETTY-20260330'
    AND COALESCE(NULLIF(TRIM(i.status), ''), '') = COALESCE(NULLIF(TRIM('output'), ''), '')
);
