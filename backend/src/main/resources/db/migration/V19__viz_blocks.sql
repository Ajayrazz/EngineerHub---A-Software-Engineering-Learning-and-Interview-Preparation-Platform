-- V19: 'viz' becomes an allowed lesson block type (M7.2, ADR-057). Backward compatible: existing rows are unaffected.
-- A visualization block stores only an algorithm id and a small validated input; the trace is computed in the browser.
ALTER TABLE catalog.content_blocks DROP CONSTRAINT ck_blocks_type;
ALTER TABLE catalog.content_blocks ADD CONSTRAINT ck_blocks_type CHECK (type IN ('markdown', 'code', 'callout', 'quiz', 'viz'));
