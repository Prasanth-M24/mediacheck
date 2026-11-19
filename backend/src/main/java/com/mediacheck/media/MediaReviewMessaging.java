package com.mediacheck.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

record MediaQueuedEvent(UUID jobId, UUID outboxId) {}

@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "mediacheck.messaging.enabled", havingValue = "true")
class MediaReviewMessagingConfiguration {

  @Bean
  DirectExchange mediaDeadLetterExchange() {
    return new DirectExchange("mediacheck.media.dlx", true, false);
  }

  @Bean
  Queue mediaDeadLetterQueue() {
    return new Queue("mediacheck.media.scan.dlq", true);
  }

  @Bean
  Queue mediaReviewQueue(@Value("${mediacheck.messaging.queue:mediacheck.media.scan}") String name) {
    Map<String, Object> args = new HashMap<>();
    args.put("x-dead-letter-exchange", "mediacheck.media.dlx");
    args.put("x-dead-letter-routing-key", "mediacheck.media.scan.dlq");
    return new Queue(name, true, false, false, args);
  }

  @Bean
  Binding mediaDeadLetterBinding(Queue mediaDeadLetterQueue, DirectExchange mediaDeadLetterExchange) {
    return BindingBuilder.bind(mediaDeadLetterQueue).to(mediaDeadLetterExchange).with("mediacheck.media.scan.dlq");
  }

  @Bean
  SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setDefaultRequeueRejected(false);
    factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
    factory.setAdviceChain(RetryInterceptorBuilder.stateless()
        .maxAttempts(3)
        .backOffOptions(1000, 2.0, 10000)
        .recoverer(new RejectAndDontRequeueRecoverer())
        .build());
    return factory;
  }
}

@Component
class MediaReviewJobDispatcher {
  private static final Logger log = LoggerFactory.getLogger(MediaReviewJobDispatcher.class);

  private final MediaReviewService service;
  private final RabbitTemplate rabbit;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final boolean enabled;
  private final String queue;

  MediaReviewJobDispatcher(MediaReviewService service,
                           @Autowired(required = false) RabbitTemplate rabbit,
                           JdbcTemplate jdbc,
                           ObjectMapper mapper,
                           @Value("${mediacheck.messaging.enabled:false}") boolean enabled,
                           @Value("${mediacheck.messaging.queue:mediacheck.media.scan}") String queue) {
    this.service = service;
    this.rabbit = rabbit;
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.enabled = enabled;
    this.queue = queue;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void dispatch(MediaQueuedEvent event) {
    if (enabled && rabbit != null) {
      try {
        String payload = mapper.writeValueAsString(Map.of("jobId", event.jobId().toString(), "outboxId", event.outboxId().toString()));
        Message message = MessageBuilder.withBody(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
            .setMessageId(event.jobId().toString())
            .build();
        rabbit.send(queue, message);
        jdbc.update("UPDATE outbox_messages SET status='SENT', sent_at=CURRENT_TIMESTAMP WHERE id=?", event.outboxId());
        log.info("Dispatched scan job {} to queue {} via outbox", event.jobId(), queue);
      } catch (Exception e) {
        log.warn("Immediate dispatch to RabbitMQ failed for outbox {}; poller will retry: {}", event.outboxId(), e.getMessage());
      }
    } else {
      // In-process synchronous execution when messaging is disabled
      try {
        service.process(event.jobId());
        jdbc.update("UPDATE outbox_messages SET status='SENT', sent_at=CURRENT_TIMESTAMP WHERE id=?", event.outboxId());
      } catch (Exception e) {
        log.error("Synchronous processing failed for job {}", event.jobId(), e);
        jdbc.update("UPDATE outbox_messages SET status='FAILED', error_details=? WHERE id=?", e.getMessage(), event.outboxId());
      }
    }
  }

  @RabbitListener(queues = "${mediacheck.messaging.queue:mediacheck.media.scan}", autoStartup = "${mediacheck.messaging.enabled:false}")
  public void consume(Message message) {
    String body = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
    log.info("Received scan job message: {}", body);
    UUID jobId = null;
    try {
      if (body.startsWith("{")) {
        JsonNode node = mapper.readTree(body);
        jobId = UUID.fromString(node.get("jobId").asText());
      } else {
        jobId = UUID.fromString(body.trim());
      }
    } catch (Exception e) {
      log.error("Poison message encountered, unable to parse jobId: {}", body, e);
      // Re-throw so RetryInterceptor rejects it to DLQ
      throw new IllegalArgumentException("Invalid job payload: " + body, e);
    }

    try {
      service.process(jobId);
    } catch (Exception e) {
      log.error("Error processing scan job {}: {}", jobId, e.getMessage());
      throw new RuntimeException("Job processing failed for " + jobId, e);
    }
  }

  /**
   * Transactional Outbox Poller: recovers pending messages that were not published immediately.
   */
  @Scheduled(fixedDelayString = "${mediacheck.outbox.poll-delay-ms:5000}")
  @Transactional
  public void pollOutbox() {
    if (!enabled || rabbit == null) return;
    jdbc.query("SELECT id, payload_json, retry_count FROM outbox_messages WHERE status='PENDING' AND retry_count < 5 ORDER BY created_at LIMIT 25", rs -> {
      while (rs.next()) {
        UUID outboxId = rs.getObject(1, UUID.class);
        String payload = rs.getString(2);
        int retries = rs.getInt(3);
        try {
          Message msg = MessageBuilder.withBody(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
              .build();
          rabbit.send(queue, msg);
          jdbc.update("UPDATE outbox_messages SET status='SENT', sent_at=CURRENT_TIMESTAMP WHERE id=?", outboxId);
          log.info("Outbox poller dispatched message {}", outboxId);
        } catch (Exception e) {
          log.warn("Outbox poller retry {} failed for {}: {}", retries + 1, outboxId, e.getMessage());
          if (retries + 1 >= 5) {
            jdbc.update("UPDATE outbox_messages SET status='FAILED', retry_count=retry_count+1, error_details=? WHERE id=?", e.getMessage(), outboxId);
          } else {
            jdbc.update("UPDATE outbox_messages SET retry_count=retry_count+1, error_details=? WHERE id=?", e.getMessage(), outboxId);
          }
        }
      }
      return null;
    });
  }

  /**
   * Stale Job Recovery: catches jobs that got stuck in PROCESSING (e.g. unexpected worker crash).
   */
  @Scheduled(fixedDelayString = "${mediacheck.media.recovery-delay-ms:30000}")
  public void recoverStaleJobs() {
    Timestamp cutoff = Timestamp.from(Instant.now().minusSeconds(600));
    jdbc.query("SELECT id FROM scan_jobs WHERE (status='QUEUED' OR (status='PROCESSING' AND started_at < ?)) AND id NOT IN (SELECT scan_job_id FROM decisions) LIMIT 50", rs -> {
      while (rs.next()) {
        UUID jobId = rs.getObject(1, UUID.class);
        log.warn("Recovering stale or queued job {}", jobId);
        if (enabled && rabbit != null) {
          try {
            String payload = mapper.writeValueAsString(Map.of("jobId", jobId.toString()));
            rabbit.send(queue, MessageBuilder.withBody(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build());
          } catch (Exception e) {
            log.error("Failed to republish stale job {}: {}", jobId, e.getMessage());
          }
        } else {
          try {
            service.process(jobId);
          } catch (Exception e) {
            log.error("Failed to re-process stale job {}: {}", jobId, e.getMessage());
          }
        }
      }
      return null;
    }, cutoff);
  }

  /**
   * Outbox cleanup: prune old SENT messages older than 7 days.
   */
  @Scheduled(cron = "${mediacheck.outbox.cleanup-cron:0 0 3 * * ?}")
  public void cleanupOutbox() {
    Timestamp oldCutoff = Timestamp.from(Instant.now().minusSeconds(7 * 86400));
    int deleted = jdbc.update("DELETE FROM outbox_messages WHERE status='SENT' AND sent_at < ?", oldCutoff);
    if (deleted > 0) {
      log.info("Pruned {} old outbox messages", deleted);
    }
  }
}
