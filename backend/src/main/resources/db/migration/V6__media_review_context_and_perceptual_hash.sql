ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS perceptual_hash CHAR(16);
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS intended_channel VARCHAR(100);
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS intended_territory VARCHAR(100);
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS intended_use VARCHAR(200);
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS planned_publication_at TIMESTAMPTZ;
ALTER TABLE rights_assets ADD COLUMN IF NOT EXISTS perceptual_hash CHAR(16);
CREATE INDEX IF NOT EXISTS idx_media_assets_company_phash ON media_assets (company_id, perceptual_hash);
CREATE INDEX IF NOT EXISTS idx_rights_assets_company_phash ON rights_assets (company_id, perceptual_hash);

CREATE TABLE IF NOT EXISTS outbox_messages (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
  event_type VARCHAR(100) NOT NULL,
  payload_json TEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
  retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
  error_details TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  sent_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_messages (status, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_company ON outbox_messages (company_id);
