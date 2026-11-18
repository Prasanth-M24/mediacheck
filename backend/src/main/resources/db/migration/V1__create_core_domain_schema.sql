CREATE TABLE companies (
  id UUID PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  slug VARCHAR(100) NOT NULL UNIQUE,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
  id UUID PRIMARY KEY,
  email VARCHAR(320) NOT NULL UNIQUE,
  display_name VARCHAR(200) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INVITED', 'SUSPENDED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE company_memberships (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  membership_role VARCHAR(32) NOT NULL CHECK (membership_role IN ('OWNER', 'ADMIN', 'REVIEWER', 'MEMBER')),
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INVITED', 'REVOKED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (company_id, user_id),
  UNIQUE (company_id, user_id, id)
);

CREATE TABLE rights_assets (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  title VARCHAR(300) NOT NULL,
  asset_type VARCHAR(64) NOT NULL,
  external_reference VARCHAR(200),
  rights_holder VARCHAR(300),
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (company_id, id),
  UNIQUE (company_id, external_reference)
);

CREATE TABLE licence_records (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  licence_reference VARCHAR(200) NOT NULL,
  licensor_name VARCHAR(300) NOT NULL,
  licensee_name VARCHAR(300),
  valid_from TIMESTAMPTZ,
  valid_until TIMESTAMPTZ,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('DRAFT', 'ACTIVE', 'EXPIRED', 'REVOKED')),
  terms_json JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (company_id, licence_reference),
  UNIQUE (company_id, id),
  CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from)
);

CREATE TABLE licence_rights_assets (
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  licence_record_id UUID NOT NULL,
  rights_asset_id UUID NOT NULL,
  FOREIGN KEY (company_id, licence_record_id) REFERENCES licence_records(company_id, id) ON DELETE CASCADE,
  FOREIGN KEY (company_id, rights_asset_id) REFERENCES rights_assets(company_id, id) ON DELETE RESTRICT,
  PRIMARY KEY (licence_record_id, rights_asset_id),
  UNIQUE (company_id, licence_record_id, rights_asset_id)
);

CREATE TABLE licence_documents (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  licence_record_id UUID NOT NULL,
  storage_key VARCHAR(512) NOT NULL,
  original_filename VARCHAR(512) NOT NULL,
  mime_type VARCHAR(127) NOT NULL,
  sha256_checksum CHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (company_id, storage_key),
  FOREIGN KEY (company_id, licence_record_id) REFERENCES licence_records(company_id, id) ON DELETE CASCADE
);

CREATE TABLE company_policies (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  name VARCHAR(200) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (company_id, id),
  UNIQUE (company_id, name)
);

CREATE TABLE policy_versions (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  policy_id UUID NOT NULL,
  version_number INTEGER NOT NULL CHECK (version_number > 0),
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
  rules_json JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMPTZ,
  UNIQUE (company_id, id),
  UNIQUE (policy_id, version_number),
  FOREIGN KEY (company_id, policy_id) REFERENCES company_policies(company_id, id) ON DELETE RESTRICT
);

CREATE TABLE media_assets (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  uploaded_by_user_id UUID NOT NULL,
  uploaded_by_membership_id UUID NOT NULL,
  original_filename VARCHAR(512) NOT NULL,
  storage_key VARCHAR(512) NOT NULL,
  storage_provider VARCHAR(64) NOT NULL,
  mime_type VARCHAR(127) NOT NULL CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp')),
  file_size_bytes BIGINT NOT NULL CHECK (file_size_bytes >= 0),
  sha256_checksum CHAR(64) NOT NULL,
  width_pixels INTEGER CHECK (width_pixels > 0),
  height_pixels INTEGER CHECK (height_pixels > 0),
  lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' CHECK (lifecycle_status IN ('UPLOADED', 'AVAILABLE', 'QUARANTINED', 'DELETED')),
  uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (company_id, storage_key),
  UNIQUE (company_id, id),
  UNIQUE (company_id, sha256_checksum),
  FOREIGN KEY (company_id, uploaded_by_user_id, uploaded_by_membership_id) REFERENCES company_memberships(company_id, user_id, id) ON DELETE RESTRICT
);

CREATE TABLE scan_jobs (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  media_asset_id UUID NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')),
  retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
  error_details TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  UNIQUE (company_id, id),
  CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at),
  FOREIGN KEY (company_id, media_asset_id) REFERENCES media_assets(company_id, id) ON DELETE RESTRICT
);

CREATE TABLE pipeline_runs (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  scan_job_id UUID NOT NULL,
  pipeline_type VARCHAR(64) NOT NULL CHECK (pipeline_type IN ('PROVENANCE', 'RIGHTS', 'DEPLOYMENT_READINESS')),
  status VARCHAR(32) NOT NULL CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'UNAVAILABLE', 'TIMED_OUT')),
  pipeline_version VARCHAR(100),
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  details_json JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (company_id, id),
  FOREIGN KEY (company_id, scan_job_id) REFERENCES scan_jobs(company_id, id) ON DELETE CASCADE
);

CREATE TABLE check_results (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  pipeline_run_id UUID NOT NULL,
  check_name VARCHAR(200) NOT NULL,
  status VARCHAR(32) NOT NULL CHECK (status IN ('PASS', 'FAIL', 'ADVISORY_FLAG', 'NOT_APPLICABLE', 'UNAVAILABLE', 'UNKNOWN')),
  summary TEXT NOT NULL,
  details_json JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (company_id, id),
  UNIQUE (pipeline_run_id, check_name),
  FOREIGN KEY (company_id, pipeline_run_id) REFERENCES pipeline_runs(company_id, id) ON DELETE CASCADE
);

CREATE TABLE findings (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  pipeline_run_id UUID NOT NULL,
  external_reference VARCHAR(200),
  finding_type VARCHAR(100) NOT NULL,
  status VARCHAR(32) NOT NULL CHECK (status IN ('PASS', 'FAIL', 'ADVISORY_FLAG', 'NOT_APPLICABLE', 'UNAVAILABLE', 'UNKNOWN')),
  summary TEXT NOT NULL,
  details_json JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (company_id, id),
  UNIQUE (pipeline_run_id, external_reference),
  FOREIGN KEY (company_id, pipeline_run_id) REFERENCES pipeline_runs(company_id, id) ON DELETE CASCADE
);

CREATE TABLE evidence_records (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  pipeline_run_id UUID NOT NULL,
  finding_id UUID,
  evidence_type VARCHAR(100) NOT NULL,
  storage_key VARCHAR(512),
  checksum CHAR(64),
  details_json JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (company_id, storage_key),
  FOREIGN KEY (company_id, pipeline_run_id) REFERENCES pipeline_runs(company_id, id) ON DELETE CASCADE,
  FOREIGN KEY (company_id, finding_id) REFERENCES findings(company_id, id) ON DELETE RESTRICT
);

CREATE TABLE decisions (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  scan_job_id UUID NOT NULL,
  policy_version_id UUID NOT NULL,
  decision VARCHAR(32) NOT NULL CHECK (decision IN ('APPROVE', 'REVIEW', 'BLOCK')),
  fired_rule_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
  finding_references JSONB NOT NULL DEFAULT '[]'::jsonb,
  reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
  decided_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (company_id, id),
  FOREIGN KEY (company_id, scan_job_id) REFERENCES scan_jobs(company_id, id) ON DELETE RESTRICT,
  FOREIGN KEY (company_id, policy_version_id) REFERENCES policy_versions(company_id, id) ON DELETE RESTRICT,
  UNIQUE (scan_job_id)
);

CREATE TABLE reports (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  decision_id UUID NOT NULL,
  storage_key VARCHAR(512),
  report_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' CHECK (report_status IN ('PENDING', 'AVAILABLE', 'FAILED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (company_id, decision_id) REFERENCES decisions(company_id, id) ON DELETE RESTRICT,
  UNIQUE (decision_id)
);

CREATE TABLE review_actions (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  scan_job_id UUID NOT NULL,
  actor_user_id UUID NOT NULL,
  actor_membership_id UUID NOT NULL,
  action_type VARCHAR(64) NOT NULL,
  note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (company_id, scan_job_id) REFERENCES scan_jobs(company_id, id) ON DELETE RESTRICT,
  FOREIGN KEY (company_id, actor_user_id, actor_membership_id) REFERENCES company_memberships(company_id, user_id, id) ON DELETE RESTRICT
);

CREATE TABLE audit_events (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
  actor_user_id UUID,
  actor_membership_id UUID,
  event_type VARCHAR(100) NOT NULL,
  subject_type VARCHAR(100) NOT NULL,
  subject_id UUID,
  metadata_json JSONB,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (company_id, actor_user_id, actor_membership_id) REFERENCES company_memberships(company_id, user_id, id) ON DELETE RESTRICT,
  CHECK ((actor_user_id IS NULL) = (actor_membership_id IS NULL))
);

CREATE INDEX idx_memberships_user_company ON company_memberships (user_id, company_id);
CREATE INDEX idx_rights_assets_company_status ON rights_assets (company_id, status);
CREATE INDEX idx_licence_records_company_status ON licence_records (company_id, status);
CREATE INDEX idx_licence_documents_licence ON licence_documents (licence_record_id);
CREATE INDEX idx_policy_versions_company_policy ON policy_versions (company_id, policy_id);
CREATE INDEX idx_media_assets_company_uploaded ON media_assets (company_id, uploaded_at DESC);
CREATE INDEX idx_scan_jobs_company_status_created ON scan_jobs (company_id, status, created_at DESC);
CREATE INDEX idx_pipeline_runs_scan_job ON pipeline_runs (scan_job_id, created_at);
CREATE INDEX idx_check_results_pipeline_run ON check_results (pipeline_run_id);
CREATE INDEX idx_findings_pipeline_run ON findings (pipeline_run_id);
CREATE INDEX idx_evidence_pipeline_run ON evidence_records (pipeline_run_id);
CREATE INDEX idx_decisions_company_decided ON decisions (company_id, decided_at DESC);
CREATE INDEX idx_review_actions_company_scan ON review_actions (company_id, scan_job_id, created_at);
CREATE INDEX idx_audit_events_company_occurred ON audit_events (company_id, occurred_at DESC);
