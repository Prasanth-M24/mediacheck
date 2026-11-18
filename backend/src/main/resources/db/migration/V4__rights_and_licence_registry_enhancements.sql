-- Phase 2.3 Rights & Licence Registry Schema Enhancements

-- 1. Extend rights_assets with supplementary metadata
ALTER TABLE rights_assets ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE rights_assets ADD COLUMN IF NOT EXISTS original_filename VARCHAR(512);
ALTER TABLE rights_assets ADD COLUMN IF NOT EXISTS mime_type VARCHAR(127);
ALTER TABLE rights_assets ADD COLUMN IF NOT EXISTS file_size BIGINT;
ALTER TABLE rights_assets ADD COLUMN IF NOT EXISTS sha256_checksum CHAR(64);
ALTER TABLE rights_assets ADD COLUMN IF NOT EXISTS storage_key VARCHAR(512);
ALTER TABLE rights_assets ADD COLUMN IF NOT EXISTS created_by_membership_id UUID;

-- 2. Extend licence_records with name, description, licence_type, and creator
ALTER TABLE licence_records ADD COLUMN IF NOT EXISTS name VARCHAR(300);
ALTER TABLE licence_records ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE licence_records ADD COLUMN IF NOT EXISTS licence_type VARCHAR(64);
ALTER TABLE licence_records ADD COLUMN IF NOT EXISTS created_by_membership_id UUID;

-- 3. Extend licence_documents with file size and uploader
ALTER TABLE licence_documents ADD COLUMN IF NOT EXISTS file_size BIGINT;
ALTER TABLE licence_documents ADD COLUMN IF NOT EXISTS uploaded_by_membership_id UUID;

-- 4. Status constraint updates
ALTER TABLE rights_assets DROP CONSTRAINT IF EXISTS rights_assets_status_check;
ALTER TABLE rights_assets ADD CONSTRAINT rights_assets_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED', 'INACTIVE'));

ALTER TABLE licence_records DROP CONSTRAINT IF EXISTS licence_records_status_check;
ALTER TABLE licence_records ADD CONSTRAINT licence_records_status_check CHECK (status IN ('DRAFT', 'ACTIVE', 'EXPIRED', 'REVOKED', 'SUSPENDED'));

-- 5. Additional performance indexes
CREATE INDEX IF NOT EXISTS idx_rights_assets_company_type ON rights_assets(company_id, asset_type);
CREATE INDEX IF NOT EXISTS idx_rights_assets_company_created ON rights_assets(company_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_licence_records_valid_until ON licence_records(company_id, valid_until);
CREATE INDEX IF NOT EXISTS idx_licence_records_company_created ON licence_records(company_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_licence_documents_company ON licence_documents(company_id);

-- Membership references must remain inside the owning company. The existing
-- (company_id, id) uniqueness on company_memberships makes these composite
-- foreign keys enforceable without changing V1.
ALTER TABLE rights_assets DROP CONSTRAINT IF EXISTS rights_assets_created_by_membership_id_fkey;
ALTER TABLE rights_assets ADD CONSTRAINT fk_rights_assets_created_by_membership
  FOREIGN KEY (company_id, created_by_membership_id)
  REFERENCES company_memberships(company_id, id) ON DELETE RESTRICT;

ALTER TABLE licence_records DROP CONSTRAINT IF EXISTS licence_records_created_by_membership_id_fkey;
ALTER TABLE licence_records ADD CONSTRAINT fk_licence_records_created_by_membership
  FOREIGN KEY (company_id, created_by_membership_id)
  REFERENCES company_memberships(company_id, id) ON DELETE RESTRICT;

ALTER TABLE licence_documents DROP CONSTRAINT IF EXISTS licence_documents_uploaded_by_membership_id_fkey;
ALTER TABLE licence_documents ADD CONSTRAINT fk_licence_documents_uploaded_by_membership
  FOREIGN KEY (company_id, uploaded_by_membership_id)
  REFERENCES company_memberships(company_id, id) ON DELETE RESTRICT;
