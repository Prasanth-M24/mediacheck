ALTER TABLE reports ADD COLUMN IF NOT EXISTS report_json JSONB;
ALTER TABLE reports ADD COLUMN IF NOT EXISTS explanation_status VARCHAR(32) NOT NULL DEFAULT 'UNAVAILABLE';
CREATE INDEX IF NOT EXISTS idx_reports_company_created ON reports (company_id, created_at DESC);
