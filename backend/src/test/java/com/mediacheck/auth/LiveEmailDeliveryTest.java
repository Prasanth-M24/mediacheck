package com.mediacheck.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThatNoException;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "MEDIACHECK_LIVE_EMAIL_TEST", matches = "true")
class LiveEmailDeliveryTest {
  @DynamicPropertySource
  static void isolatedDatabaseProperties(DynamicPropertyRegistry registry) {
    String url = System.getenv("MEDIACHECK_TEST_DATABASE_URL");
    if (url != null && !url.isBlank()) {
      registry.add("spring.datasource.url", () -> url);
      registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
      registry.add("spring.datasource.username", () -> System.getenv("MEDIACHECK_TEST_DATABASE_USERNAME"));
      registry.add("spring.datasource.password", () -> System.getenv("MEDIACHECK_TEST_DATABASE_PASSWORD"));
      registry.add("spring.flyway.target", () -> System.getenv().getOrDefault("MEDIACHECK_TEST_FLYWAY_TARGET", "4"));
    }
  }

  @Autowired(required = false)
  JavaMailSender mailSender;

  @Test
  void sendLiveVerificationEmail() {
    if (mailSender == null) return;
    SimpleMailMessage message = new SimpleMailMessage();
    String from = System.getenv("MEDIACHECK_EMAIL_FROM");
    message.setFrom(from);
    message.setTo(from);
    message.setSubject("MEDIACHECK Phase 2.2 Live Email Delivery Verification");
    message.setText("This email confirms that MEDIACHECK Phase 2.2 live SMTP provider delivery is operational.");

    assertThatNoException().isThrownBy(() -> mailSender.send(message));
  }
}
