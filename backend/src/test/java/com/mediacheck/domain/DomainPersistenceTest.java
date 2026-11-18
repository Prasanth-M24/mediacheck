package com.mediacheck.domain;

import com.mediacheck.repository.CompanyMembershipRepository;
import com.mediacheck.repository.CompanyRepository;
import com.mediacheck.repository.PlatformUserRepository;
import java.time.Instant;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.UUID;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DomainPersistenceTest {
  @Autowired CompanyRepository companies;
  @Autowired PlatformUserRepository users;
  @Autowired CompanyMembershipRepository memberships;
  @Autowired TestEntityManager entityManager;
  @Autowired JdbcTemplate jdbc;

  @Test
  void persistsGeneratedUuidAndCompanyMembershipWithTenantScopedLookup() {
    var company = companies.saveAndFlush(new Company("Example Studio", "example-studio"));
    var user = users.saveAndFlush(new PlatformUser("reviewer@example.test", "Reviewer"));
    memberships.saveAndFlush(new CompanyMembership(company, user, MembershipRole.REVIEWER));

    assertThat(company.getId()).isNotNull();
    assertThat(memberships.findByCompany_IdAndUser_Id(company.getId(), user.getId())).isPresent();
    assertThat(memberships.findByCompany_IdAndUser_Id(java.util.UUID.randomUUID(), user.getId())).isEmpty();
  }

  @Test
  void rejectsDuplicateCompanyMembership() {
    var company = companies.saveAndFlush(new Company("Example Studio", "example-studio"));
    var user = users.saveAndFlush(new PlatformUser("reviewer@example.test", "Reviewer"));
    memberships.saveAndFlush(new CompanyMembership(company, user, MembershipRole.REVIEWER));
    memberships.save(new CompanyMembership(company, user, MembershipRole.ADMIN));

    assertThatThrownBy(memberships::flush).isInstanceOf(RuntimeException.class);
  }

  @Test
  void rejectsMissingRequiredCompanyName() {
    assertThatThrownBy(() -> insert("INSERT INTO companies(id,name,slug) VALUES (?,NULL,?)", UUID.randomUUID(), "missing-name"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void flywayCreatesAllTablesAndHibernateValidatesTheMigratedSchema() {
    Integer tableCount = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name IN ('companies','users','company_memberships','rights_assets','licence_records','licence_rights_assets','licence_documents','company_policies','policy_versions','media_assets','scan_jobs','pipeline_runs','check_results','findings','evidence_records','decisions','reports','review_actions','audit_events')", Integer.class);
    assertThat(tableCount).isEqualTo(19);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema = 'public' AND constraint_type = 'FOREIGN KEY'", Integer.class)).isEqualTo(42);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name IN ('refresh_sessions','company_invitations','account_action_tokens')", Integer.class)).isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1','2') AND success", Integer.class)).isEqualTo(2);
  }

  @Test
  void persistsCoreRightsMediaScanEvidenceDecisionReviewAndAuditRecords() {
    var company = companies.saveAndFlush(new Company("Workflow Studio", "workflow-studio"));
    var user = users.saveAndFlush(new PlatformUser("owner@example.test", "Owner"));
    var membership = memberships.saveAndFlush(new CompanyMembership(company, user, MembershipRole.OWNER));
    UUID companyId = company.getId();
    UUID userId = user.getId();
    UUID membershipId = membership.getId();

    UUID rightsId = UUID.randomUUID();
    insert("INSERT INTO rights_assets(id,company_id,title,asset_type) VALUES (?,?,?,?)", rightsId, companyId, "Registered image", "IMAGE");
    UUID licenceId = UUID.randomUUID();
    insert("INSERT INTO licence_records(id,company_id,licence_reference,licensor_name) VALUES (?,?,?,?)", licenceId, companyId, "LIC-1", "Licensor");
    insert("INSERT INTO licence_rights_assets(company_id,licence_record_id,rights_asset_id) VALUES (?,?,?)", companyId, licenceId, rightsId);
    insert("INSERT INTO licence_documents(id,company_id,licence_record_id,storage_key,original_filename,mime_type) VALUES (?,?,?,?,?,?)", UUID.randomUUID(), companyId, licenceId, "private/licence-1", "licence.pdf", "application/pdf");

    UUID policyId = UUID.randomUUID();
    insert("INSERT INTO company_policies(id,company_id,name) VALUES (?,?,?)", policyId, companyId, "Default");
    UUID policyVersionId = UUID.randomUUID();
    insert("INSERT INTO policy_versions(id,company_id,policy_id,version_number,rules_json) VALUES (?,?,?,?,CAST(? AS jsonb))", policyVersionId, companyId, policyId, 1, "{}");

    UUID mediaId = UUID.randomUUID();
    insert("INSERT INTO media_assets(id,company_id,uploaded_by_user_id,uploaded_by_membership_id,original_filename,storage_key,storage_provider,mime_type,file_size_bytes,sha256_checksum) VALUES (?,?,?,?,?,?,?,?,?,?)", mediaId, companyId, userId, membershipId, "sample.png", "company/sample.png", "local", "image/png", 12L, "a".repeat(64));
    UUID scanId = UUID.randomUUID();
    insert("INSERT INTO scan_jobs(id,company_id,media_asset_id) VALUES (?,?,?)", scanId, companyId, mediaId);
    assertThat(jdbc.update("UPDATE scan_jobs SET status='PROCESSING',started_at=CURRENT_TIMESTAMP WHERE id=?", scanId)).isEqualTo(1);
    assertThat(jdbc.update("UPDATE scan_jobs SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE id=?", scanId)).isEqualTo(1);
    UUID pipelineId = UUID.randomUUID();
    insert("INSERT INTO pipeline_runs(id,company_id,scan_job_id,pipeline_type,status) VALUES (?,?,?,?,?)", pipelineId, companyId, scanId, "PROVENANCE", "COMPLETED");
    UUID findingId = UUID.randomUUID();
    insert("INSERT INTO findings(id,company_id,pipeline_run_id,finding_type,status,summary) VALUES (?,?,?,?,?,?)", findingId, companyId, pipelineId, "PROVENANCE", "PASS", "Evidence parsed");
    insert("INSERT INTO check_results(id,company_id,pipeline_run_id,check_name,status,summary) VALUES (?,?,?,?,?,?)", UUID.randomUUID(), companyId, pipelineId, "metadata", "PASS", "Metadata check complete");
    insert("INSERT INTO evidence_records(id,company_id,pipeline_run_id,finding_id,evidence_type) VALUES (?,?,?,?,?)", UUID.randomUUID(), companyId, pipelineId, findingId, "METADATA");
    UUID decisionId = UUID.randomUUID();
    insert("INSERT INTO decisions(id,company_id,scan_job_id,policy_version_id,decision) VALUES (?,?,?,?,?)", decisionId, companyId, scanId, policyVersionId, "REVIEW");
    insert("INSERT INTO reports(id,company_id,decision_id) VALUES (?,?,?)", UUID.randomUUID(), companyId, decisionId);
    insert("INSERT INTO review_actions(id,company_id,scan_job_id,actor_user_id,actor_membership_id,action_type) VALUES (?,?,?,?,?,?)", UUID.randomUUID(), companyId, scanId, userId, membershipId, "CONFIRM_REVIEW");

    var savedEvent = entityManager.persistFlushFind(new AuditEvent(company, "SCAN_REVIEWED", "SCAN_JOB", scanId, "{\"source\":\"test\"}", Instant.parse("2026-01-01T00:00:00Z")));
    entityManager.clear();
    var loadedEvent = entityManager.find(AuditEvent.class, savedEvent.getId());
    assertThat(loadedEvent.getEventType()).isEqualTo("SCAN_REVIEWED");
    assertThat(loadedEvent.getSubjectType()).isEqualTo("SCAN_JOB");
    assertThat(loadedEvent.getSubjectId()).isEqualTo(scanId);
    assertThat(loadedEvent.getCompany().getId()).isEqualTo(companyId);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM licence_rights_assets WHERE licence_record_id=? AND rights_asset_id=?", Integer.class, licenceId, rightsId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports WHERE decision_id=?", Integer.class, decisionId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE subject_id=?", Integer.class, scanId)).isEqualTo(1);
  }

  @Test
  void rejectsCrossCompanyMediaAndPolicyReferences() {
    var companyA = companies.saveAndFlush(new Company("Company A", "company-a"));
    var companyB = companies.saveAndFlush(new Company("Company B", "company-b"));
    var userA = users.saveAndFlush(new PlatformUser("a@example.test", "A"));
    var membershipA = memberships.saveAndFlush(new CompanyMembership(companyA, userA, MembershipRole.OWNER));
    var userB = users.saveAndFlush(new PlatformUser("b@example.test", "B"));
    var membershipB = memberships.saveAndFlush(new CompanyMembership(companyB, userB, MembershipRole.OWNER));
    UUID mediaB = UUID.randomUUID();
    insert("INSERT INTO media_assets(id,company_id,uploaded_by_user_id,uploaded_by_membership_id,original_filename,storage_key,storage_provider,mime_type,file_size_bytes,sha256_checksum) VALUES (?,?,?,?,?,?,?,?,?,?)", mediaB, companyB.getId(), userB.getId(), membershipB.getId(), "b.png", "b.png", "local", "image/png", 1L, "b".repeat(64));

    assertRejectedInsert("INSERT INTO scan_jobs(id,company_id,media_asset_id) VALUES (?,?,?)", UUID.randomUUID(), companyA.getId(), mediaB);
    assertRejectedInsert("INSERT INTO audit_events(id,company_id,actor_user_id,actor_membership_id,event_type,subject_type) VALUES (?,?,?,?,?,?)", UUID.randomUUID(), companyA.getId(), userB.getId(), membershipB.getId(), "CROSS_TENANT", "TEST");

    UUID policyA = UUID.randomUUID();
    insert("INSERT INTO company_policies(id,company_id,name) VALUES (?,?,?)", policyA, companyA.getId(), "Policy A");
    UUID versionB = UUID.randomUUID();
    insert("INSERT INTO company_policies(id,company_id,name) VALUES (?,?,?)", UUID.randomUUID(), companyB.getId(), "Policy B");
    UUID policyB = jdbc.queryForObject("SELECT id FROM company_policies WHERE company_id=?", UUID.class, companyB.getId());
    insert("INSERT INTO policy_versions(id,company_id,policy_id,version_number,rules_json) VALUES (?,?,?,?,CAST(? AS jsonb))", versionB, companyB.getId(), policyB, 1, "{}");
    UUID mediaA = UUID.randomUUID();
    insert("INSERT INTO media_assets(id,company_id,uploaded_by_user_id,uploaded_by_membership_id,original_filename,storage_key,storage_provider,mime_type,file_size_bytes,sha256_checksum) VALUES (?,?,?,?,?,?,?,?,?,?)", mediaA, companyA.getId(), userA.getId(), membershipA.getId(), "a.png", "a.png", "local", "image/png", 1L, "c".repeat(64));
    UUID scanA = UUID.randomUUID();
    insert("INSERT INTO scan_jobs(id,company_id,media_asset_id) VALUES (?,?,?)", scanA, companyA.getId(), mediaA);
    assertRejectedInsert("INSERT INTO decisions(id,company_id,scan_job_id,policy_version_id,decision) VALUES (?,?,?,?,?)", UUID.randomUUID(), companyA.getId(), scanA, versionB, "REVIEW");
    assertThat(policyA).isNotNull();
  }

  private void insert(String sql, Object... arguments) {
    jdbc.update(sql, arguments);
  }

  private void assertRejectedInsert(String sql, Object... arguments) {
    assertThatThrownBy(() -> jdbc.execute((ConnectionCallback<Void>) connection -> {
      Savepoint savepoint = connection.setSavepoint();
      try (var statement = connection.prepareStatement(sql)) {
        new ArgumentPreparedStatementSetter(arguments).setValues(statement);
        statement.executeUpdate();
      } catch (SQLException exception) {
        connection.rollback(savepoint);
        throw exception;
      } finally {
        connection.releaseSavepoint(savepoint);
      }
      return null;
    })).isInstanceOf(DataIntegrityViolationException.class);
  }
}
