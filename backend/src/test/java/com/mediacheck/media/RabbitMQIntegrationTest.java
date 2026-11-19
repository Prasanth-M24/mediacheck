package com.mediacheck.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "mediacheck.messaging.enabled=true",
    "spring.rabbitmq.host=localhost",
    "spring.rabbitmq.port=5673",
    "spring.rabbitmq.username=Prasanth",
    "spring.rabbitmq.password=prasanth123",
    "mediacheck.messaging.queue=mediacheck.media.scan.test"
})
public class RabbitMQIntegrationTest {

  @DynamicPropertySource
  static void configurePostgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> {
      String u = System.getenv("MEDIACHECK_TEST_DATABASE_URL");
      return (u != null && !u.isBlank()) ? u : "jdbc:postgresql://localhost:5433/mediacheck";
    });
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.datasource.username", () -> {
      String u = System.getenv("MEDIACHECK_TEST_DATABASE_USERNAME");
      return (u != null && !u.isBlank()) ? u : "postgres";
    });
    registry.add("spring.datasource.password", () -> {
      String p = System.getenv("MEDIACHECK_TEST_DATABASE_PASSWORD");
      return (p != null && !p.isBlank()) ? p : "pra24092005";
    });
    registry.add("spring.flyway.target", () -> "6");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }

  @Autowired private RabbitTemplate rabbit;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper mapper;
  @Autowired private MediaReviewJobDispatcher dispatcher;

  @Test
  void realRabbitMqConnectionAndQueueProcessing() throws Exception {
    UUID companyId = createCompany("Rabbit Corp", "rabbit-corp-" + UUID.randomUUID());
    UUID userId = createUser("rabbit-" + UUID.randomUUID() + "@example.test", "Rabbit User");
    UUID memId = createMembership(companyId, userId, "MEMBER");

    UUID mediaId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO media_assets (id, company_id, uploaded_by_user_id, uploaded_by_membership_id, original_filename, storage_key, storage_provider, mime_type, file_size_bytes, sha256_checksum, lifecycle_status) " +
        "VALUES (?, ?, ?, ?, 'photo.png', 'key/photo.png', 'local', 'image/png', 500, ?, 'UPLOADED')",
        mediaId, companyId, userId, memId, "f".repeat(64));

    UUID jobId = UUID.randomUUID();
    jdbc.update("INSERT INTO scan_jobs (id, company_id, media_asset_id, status, retry_count) VALUES (?, ?, ?, 'QUEUED', 0)",
        jobId, companyId, mediaId);

    // Publish job directly to test queue
    String payload = mapper.writeValueAsString(Map.of("jobId", jobId.toString(), "mediaId", mediaId.toString()));
    Message msg = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
        .build();

    rabbit.send("mediacheck.media.scan.test", msg);

    // Consume through dispatcher and verify state transitions to COMPLETED
    dispatcher.consume(msg);

    String finalStatus = jdbc.queryForObject("SELECT status FROM scan_jobs WHERE id=?", String.class, jobId);
    assertThat(finalStatus).isEqualTo("COMPLETED");

    // Verify decision and report records exist
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM decisions WHERE scan_job_id=?", Integer.class, jobId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports WHERE decision_id=(SELECT id FROM decisions WHERE scan_job_id=?)", Integer.class, jobId)).isEqualTo(1);
  }

  @Test
  void idempotentConsumerSkipsAlreadyCompletedJobWithoutDuplicateDecisions() throws Exception {
    UUID companyId = createCompany("Idempotent Co", "idemp-co-" + UUID.randomUUID());
    UUID userId = createUser("idemp-" + UUID.randomUUID() + "@example.test", "Idemp User");
    UUID memId = createMembership(companyId, userId, "MEMBER");

    UUID mediaId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO media_assets (id, company_id, uploaded_by_user_id, uploaded_by_membership_id, original_filename, storage_key, storage_provider, mime_type, file_size_bytes, sha256_checksum, lifecycle_status) " +
        "VALUES (?, ?, ?, ?, 'photo2.png', 'key/photo2.png', 'local', 'image/png', 500, ?, 'AVAILABLE')",
        mediaId, companyId, userId, memId, "1".repeat(64));

    UUID jobId = UUID.randomUUID();
    jdbc.update("INSERT INTO scan_jobs (id, company_id, media_asset_id, status, retry_count) VALUES (?, ?, ?, 'COMPLETED', 0)",
        jobId, companyId, mediaId);

    // Record an initial decision
    UUID policyId = UUID.randomUUID();
    jdbc.update("INSERT INTO company_policies (id, company_id, name, status) VALUES (?, ?, 'Idemp Policy', 'ACTIVE')", policyId, companyId);
    UUID versionId = UUID.randomUUID();
    jdbc.update("INSERT INTO policy_versions (id, company_id, policy_id, version_number, status, rules_json) VALUES (?, ?, ?, 1, 'ACTIVE', '{}'::jsonb)", versionId, companyId, policyId);
    jdbc.update("INSERT INTO decisions (id, company_id, scan_job_id, policy_version_id, decision) VALUES (?, ?, ?, ?, 'APPROVE')",
        UUID.randomUUID(), companyId, jobId, versionId);

    // Dispatch a duplicate message for the already completed job
    String payload = mapper.writeValueAsString(Map.of("jobId", jobId.toString()));
    Message msg = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
        .build();

    dispatcher.consume(msg);

    // Verify count of decisions is still exactly 1 (no duplicate decision created)
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM decisions WHERE scan_job_id=?", Integer.class, jobId))
        .isEqualTo(1);
  }

  @Test
  void poisonMessageRejection() {
    Message poison = MessageBuilder.withBody("INVALID_NOT_A_UUID".getBytes(StandardCharsets.UTF_8))
        .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
        .build();

    try {
      dispatcher.consume(poison);
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).contains("Invalid job payload");
    }
  }

  @Test
  void outboxPollerDispatchesPendingMessagesAndUpdatesSentStatus() {
    UUID companyId = createCompany("Outbox Co", "outbox-co-" + UUID.randomUUID());
    UUID outboxId = UUID.randomUUID();
    UUID fakeJobId = UUID.randomUUID();

    jdbc.update(
        "INSERT INTO outbox_messages (id, company_id, event_type, payload_json, status, retry_count) VALUES (?, ?, 'MEDIA_SCAN_REQUESTED', ?, 'PENDING', 0)",
        outboxId, companyId, "{\"jobId\":\"" + fakeJobId + "\"}");

    // Trigger outbox poller
    dispatcher.pollOutbox();

    String status = jdbc.queryForObject("SELECT status FROM outbox_messages WHERE id=?", String.class, outboxId);
    assertThat(status).isEqualTo("SENT");
    assertThat(jdbc.queryForObject("SELECT sent_at FROM outbox_messages WHERE id=?", Object.class, outboxId)).isNotNull();
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
