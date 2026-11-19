package com.mediacheck.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "MEDIACHECK_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
public class PostgresPhase3IntegrationTest {

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("MEDIACHECK_TEST_DATABASE_URL"));
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.datasource.username", () -> System.getenv("MEDIACHECK_TEST_DATABASE_USERNAME"));
    registry.add("spring.datasource.password", () -> System.getenv("MEDIACHECK_TEST_DATABASE_PASSWORD"));
    registry.add("spring.flyway.target", () -> "6");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager txManager;

  @Test
  void flywayMigrationsV1ThroughV6Validation() {
    // Verify all 6 migrations applied successfully
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1','2','3','4','5','6') AND success = true", Integer.class))
        .isEqualTo(6);

    // Verify V5 columns and indexes on reports
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='reports' AND column_name IN ('report_json', 'explanation_status')",
        Integer.class)).isEqualTo(2);

    // Verify V6 columns on media_assets, rights_assets and outbox_messages table
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='media_assets' AND column_name IN ('perceptual_hash', 'intended_channel', 'intended_territory', 'intended_use', 'planned_publication_at')",
        Integer.class)).isEqualTo(5);

    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='rights_assets' AND column_name='perceptual_hash'",
        Integer.class)).isEqualTo(1);

    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='outbox_messages'",
        Integer.class)).isEqualTo(1);
  }

  @Test
  void mediaReviewPersistenceAndTenantIsolation() {
    UUID companyA = createCompany("Alpha Media Corp", "alpha-corp-" + UUID.randomUUID());
    UUID userA = createUser("alpha-user-" + UUID.randomUUID() + "@example.test", "Alpha User");
    UUID membershipA = createMembership(companyA, userA, "OWNER");

    UUID companyB = createCompany("Beta Studios", "beta-studios-" + UUID.randomUUID());
    UUID userB = createUser("beta-user-" + UUID.randomUUID() + "@example.test", "Beta User");
    UUID membershipB = createMembership(companyB, userB, "OWNER");

    // Persist media asset in Company A
    UUID mediaId = UUID.randomUUID();
    String sha256 = "c".repeat(64);
    jdbc.update(
        "INSERT INTO media_assets (id, company_id, uploaded_by_user_id, uploaded_by_membership_id, original_filename, storage_key, storage_provider, mime_type, file_size_bytes, sha256_checksum, width_pixels, height_pixels, perceptual_hash, intended_channel, intended_territory, intended_use, planned_publication_at, lifecycle_status) " +
        "VALUES (?, ?, ?, ?, 'photo.png', 'key/photo.png', 'local', 'image/png', 2048, ?, 800, 600, '0123456789abcdef', 'SOCIAL', 'US', 'COMMERCIAL', CURRENT_TIMESTAMP, 'UPLOADED')",
        mediaId, companyA, userA, membershipA, sha256);

    // Persist scan job
    UUID jobId = UUID.randomUUID();
    jdbc.update("INSERT INTO scan_jobs (id, company_id, media_asset_id, status, retry_count) VALUES (?, ?, ?, 'QUEUED', 0)",
        jobId, companyA, mediaId);

    // Persist pipeline run, finding, evidence
    UUID pipelineRunId = UUID.randomUUID();
    jdbc.update("INSERT INTO pipeline_runs (id, company_id, scan_job_id, pipeline_type, status, pipeline_version) VALUES (?, ?, ?, 'PROVENANCE', 'COMPLETED', 'phase3-2.0')",
        pipelineRunId, companyA, jobId);

    UUID findingId = UUID.randomUUID();
    jdbc.update("INSERT INTO findings (id, company_id, pipeline_run_id, finding_type, status, summary, details_json) VALUES (?, ?, ?, 'provenance_analysis', 'PASS', 'Metadata verified', '{\"exif\":true}'::jsonb)",
        findingId, companyA, pipelineRunId);

    UUID evidenceId = UUID.randomUUID();
    jdbc.update("INSERT INTO evidence_records (id, company_id, pipeline_run_id, finding_id, evidence_type, details_json) VALUES (?, ?, ?, ?, 'PROVENANCE_EVIDENCE', '{\"format\":\"PNG\"}'::jsonb)",
        evidenceId, companyA, pipelineRunId, findingId);

    // Persist policy and policy version
    UUID policyId = UUID.randomUUID();
    jdbc.update("INSERT INTO company_policies (id, company_id, name, status) VALUES (?, ?, 'Alpha Policy', 'ACTIVE')", policyId, companyA);
    UUID policyVersionId = UUID.randomUUID();
    jdbc.update("INSERT INTO policy_versions (id, company_id, policy_id, version_number, status, rules_json) VALUES (?, ?, ?, 1, 'ACTIVE', '{}'::jsonb)", policyVersionId, companyA, policyId);

    // Persist decision
    UUID decisionId = UUID.randomUUID();
    jdbc.update("INSERT INTO decisions (id, company_id, scan_job_id, policy_version_id, decision, fired_rule_ids, finding_references, reasons) VALUES (?, ?, ?, ?, 'APPROVE', '[]'::jsonb, '[]'::jsonb, '[\"Checks passed\"]'::jsonb)",
        decisionId, companyA, jobId, policyVersionId);

    // Persist report
    UUID reportId = UUID.randomUUID();
    jdbc.update("INSERT INTO reports (id, company_id, decision_id, report_status, report_json, explanation_status) VALUES (?, ?, ?, 'AVAILABLE', '{\"decision\":\"APPROVE\"}'::jsonb, 'AVAILABLE')",
        reportId, companyA, decisionId);

    // Persist outbox message
    UUID outboxId = UUID.randomUUID();
    jdbc.update("INSERT INTO outbox_messages (id, company_id, event_type, payload_json, status) VALUES (?, ?, 'MEDIA_SCAN_REQUESTED', '{\"jobId\":\"" + jobId + "\"}', 'PENDING')",
        outboxId, companyA);

    // Tenant Isolation Check: Company A sees 1 record, Company B sees 0
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_assets WHERE company_id = ? AND id = ?", Integer.class, companyA, mediaId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_assets WHERE company_id = ? AND id = ?", Integer.class, companyB, mediaId)).isEqualTo(0);

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scan_jobs WHERE company_id = ? AND id = ?", Integer.class, companyA, jobId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scan_jobs WHERE company_id = ? AND id = ?", Integer.class, companyB, jobId)).isEqualTo(0);

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports WHERE company_id = ? AND id = ?", Integer.class, companyA, reportId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports WHERE company_id = ? AND id = ?", Integer.class, companyB, reportId)).isEqualTo(0);

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_messages WHERE company_id = ? AND id = ?", Integer.class, companyA, outboxId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_messages WHERE company_id = ? AND id = ?", Integer.class, companyB, outboxId)).isEqualTo(0);
  }

  @Test
  void duplicateSha256RejectedWithinSameCompanyAllowedAcrossCompanies() {
    UUID company1 = createCompany("Co 1", "co1-" + UUID.randomUUID());
    UUID user1 = createUser("user1-" + UUID.randomUUID() + "@example.test", "User 1");
    UUID mem1 = createMembership(company1, user1, "MEMBER");

    UUID company2 = createCompany("Co 2", "co2-" + UUID.randomUUID());
    UUID user2 = createUser("user2-" + UUID.randomUUID() + "@example.test", "User 2");
    UUID mem2 = createMembership(company2, user2, "MEMBER");

    String sha256 = "d".repeat(64);

    // Upload in company 1 succeeds
    jdbc.update(
        "INSERT INTO media_assets (id, company_id, uploaded_by_user_id, uploaded_by_membership_id, original_filename, storage_key, storage_provider, mime_type, file_size_bytes, sha256_checksum, lifecycle_status) " +
        "VALUES (?, ?, ?, ?, 'f1.jpg', 'k1', 'local', 'image/jpeg', 100, ?, 'UPLOADED')",
        UUID.randomUUID(), company1, user1, mem1, sha256);

    // Duplicate upload in same company throws DataIntegrityViolationException due to UNIQUE(company_id, sha256_checksum)
    assertThatThrownBy(() ->
        jdbc.update(
            "INSERT INTO media_assets (id, company_id, uploaded_by_user_id, uploaded_by_membership_id, original_filename, storage_key, storage_provider, mime_type, file_size_bytes, sha256_checksum, lifecycle_status) " +
            "VALUES (?, ?, ?, ?, 'f2.jpg', 'k2', 'local', 'image/jpeg', 100, ?, 'UPLOADED')",
            UUID.randomUUID(), company1, user1, mem1, sha256)
    ).isInstanceOf(DataIntegrityViolationException.class);

    // Same sha256 in company 2 succeeds because constraint is company-scoped
    int insertedInCo2 = jdbc.update(
        "INSERT INTO media_assets (id, company_id, uploaded_by_user_id, uploaded_by_membership_id, original_filename, storage_key, storage_provider, mime_type, file_size_bytes, sha256_checksum, lifecycle_status) " +
        "VALUES (?, ?, ?, ?, 'f3.jpg', 'k3', 'local', 'image/jpeg', 100, ?, 'UPLOADED')",
        UUID.randomUUID(), company2, user2, mem2, sha256);
    assertThat(insertedInCo2).isEqualTo(1);
  }

  @Test
  void transactionalRollbackPreventsPartialState() {
    UUID company = createCompany("Rollback Co", "rollback-co-" + UUID.randomUUID());
    UUID user = createUser("user-rb-" + UUID.randomUUID() + "@example.test", "RB User");
    UUID mem = createMembership(company, user, "MEMBER");

    UUID mediaId = UUID.randomUUID();

    TransactionTemplate tx = new TransactionTemplate(txManager);
    assertThatThrownBy(() -> tx.execute(status -> {
      jdbc.update(
          "INSERT INTO media_assets (id, company_id, uploaded_by_user_id, uploaded_by_membership_id, original_filename, storage_key, storage_provider, mime_type, file_size_bytes, sha256_checksum, lifecycle_status) " +
          "VALUES (?, ?, ?, ?, 'rb.png', 'k/rb.png', 'local', 'image/png', 500, ?, 'UPLOADED')",
          mediaId, company, user, mem, "e".repeat(64));

      // Intentionally cause failure: invalid foreign key reference
      jdbc.update("INSERT INTO scan_jobs (id, company_id, media_asset_id) VALUES (?, ?, ?)",
          UUID.randomUUID(), company, UUID.randomUUID()); // non-existent media_asset_id
      return null;
    })).isInstanceOf(DataIntegrityViolationException.class);

    // Media asset should have rolled back completely
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_assets WHERE id = ?", Integer.class, mediaId))
        .isEqualTo(0);
  }

  private UUID createCompany(String name, String slug) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO companies (id, name, slug, status) VALUES (?, ?, ?, 'ACTIVE')", id, name, slug);
    return id;
  }

  private UUID createUser(String email, String name) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO users (id, email, display_name, status) VALUES (?, ?, ?, 'ACTIVE')", id, email, name);
    return id;
  }

  private UUID createMembership(UUID companyId, UUID userId, String role) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO company_memberships (id, company_id, user_id, membership_role, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
        id, companyId, userId, role);
    return id;
  }
}
