CREATE UNIQUE INDEX uq_companies_business_email_lower
  ON companies (lower(business_email)) WHERE business_email IS NOT NULL;

CREATE UNIQUE INDEX uq_company_invitations_pending_email
  ON company_invitations(company_id, lower(email))
  WHERE accepted_at IS NULL AND revoked_at IS NULL;
