-- Migration: add search_result_total_count, decryption_target_count to search_history (nullable INTEGER).
-- search_result_total_count: total search hit count at request time (e.g. 48).
-- decryption_target_count: rows with encrypted data eligible for decryption at request time (e.g. 37).
-- Idempotent: ADD COLUMN IF NOT EXISTS. Existing rows get NULL.

ALTER TABLE search_history ADD COLUMN IF NOT EXISTS search_result_total_count INTEGER NULL;
ALTER TABLE search_history ADD COLUMN IF NOT EXISTS decryption_target_count INTEGER NULL;
