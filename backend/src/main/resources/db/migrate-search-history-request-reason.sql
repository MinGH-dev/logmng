-- Migration: add request_reason to search_history.
-- Req: 20260317-request-reason-and-search-history-search-fields.
-- DBA: same as rejection_reason; optional length cap via CHECK or VARCHAR(500) per product; API max 500.
-- Idempotent: ADD COLUMN IF NOT EXISTS. Existing rows get request_reason = NULL (TC-13).

ALTER TABLE search_history ADD COLUMN IF NOT EXISTS request_reason TEXT NULL;

-- Optional (for ILIKE / like search performance): enable pg_trgm and add GIN index.
-- Uncomment and run when needed; requires: CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- CREATE INDEX IF NOT EXISTS idx_search_history_request_reason_gin ON search_history USING GIN (request_reason gin_trgm_ops);
