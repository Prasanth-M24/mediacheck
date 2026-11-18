ALTER TABLE companies ADD COLUMN legal_name VARCHAR(200);
ALTER TABLE companies ADD COLUMN business_email VARCHAR(320);
ALTER TABLE companies ADD COLUMN contact_number VARCHAR(40);
ALTER TABLE companies ADD COLUMN website VARCHAR(512);

ALTER TABLE users ADD COLUMN password_hash VARCHAR(100);
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0);
ALTER TABLE users ADD COLUMN locked_until TIMESTAMPTZ;
ALTER TABLE company_memberships ADD CONSTRAINT uq_company_membership_id UNIQUE (company_id,id);

CREATE TABLE refresh_sessions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
  membership_id UUID NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  replaced_by UUID REFERENCES refresh_sessions(id) ON DELETE SET NULL,
  CONSTRAINT refresh_sessions_membership_fk FOREIGN KEY(company_id,user_id,membership_id)
    REFERENCES company_memberships(company_id,user_id,id) ON DELETE CASCADE,
  CHECK (expires_at > issued_at)
);
CREATE INDEX idx_refresh_sessions_user_active ON refresh_sessions(user_id, expires_at);

CREATE TABLE company_invitations (
  id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
  invited_by_membership_id UUID NOT NULL,
  email VARCHAR(320) NOT NULL,
  membership_role VARCHAR(32) NOT NULL CHECK (membership_role IN ('ADMIN','REVIEWER','MEMBER')),
  token_hash CHAR(64) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMPTZ NOT NULL,
  accepted_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  FOREIGN KEY(company_id, invited_by_membership_id) REFERENCES company_memberships(company_id,id) ON DELETE CASCADE,
  CHECK (expires_at > created_at)
);
CREATE INDEX idx_company_invitations_expiry ON company_invitations(expires_at);

CREATE TABLE account_action_tokens (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash CHAR(64) NOT NULL UNIQUE,
  purpose VARCHAR(24) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL','RESET_PASSWORD')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  CHECK (expires_at > created_at)
);
CREATE INDEX idx_account_action_tokens_user_purpose ON account_action_tokens(user_id,purpose);
