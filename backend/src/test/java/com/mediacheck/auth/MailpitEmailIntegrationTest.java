package com.mediacheck.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "mediacheck.email.enabled=true",
    "mediacheck.email.from=notifications@mediacheck.test",
    "spring.mail.host=localhost",
    "spring.mail.port=1025",
    "spring.mail.properties.mail.smtp.auth=false",
    "spring.mail.properties.mail.smtp.starttls.enable=false",
    "spring.mail.properties.mail.smtp.starttls.required=false"
})
@EnabledIfEnvironmentVariable(named = "MEDIACHECK_MAILPIT_ENABLED", matches = "true")
class MailpitEmailIntegrationTest {
  @Autowired AccountEmailService accountEmail;
  private final ObjectMapper json = new ObjectMapper();
  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  void deliversVerificationInvitationAndResetEmailsToMailpit() throws Exception {
    String verifyEmail = "verify-" + java.util.UUID.randomUUID() + "@example.test";
    String verifyToken = "vtoken-" + java.util.UUID.randomUUID();
    accountEmail.verification(verifyEmail, verifyToken);

    String inviteEmail = "invite-" + java.util.UUID.randomUUID() + "@example.test";
    String inviteToken = "itoken-" + java.util.UUID.randomUUID();
    accountEmail.invitation(inviteEmail, inviteToken);

    String resetEmail = "reset-" + java.util.UUID.randomUUID() + "@example.test";
    String resetToken = "rtoken-" + java.util.UUID.randomUUID();
    accountEmail.passwordReset(resetEmail, resetToken);

    // Wait briefly for Mailpit to process incoming SMTP messages
    Thread.sleep(1000);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8025/api/v1/messages"))
        .timeout(Duration.ofSeconds(5))
        .GET()
        .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);

    JsonNode root = json.readTree(response.body());
    JsonNode messages = root.get("messages");
    assertThat(messages.isArray()).isTrue();
    assertThat(messages.size()).isGreaterThanOrEqualTo(3);

    boolean foundVerify = false, foundInvite = false, foundReset = false;
    for (JsonNode m : messages) {
      String subject = m.get("Subject").asText();
      String id = m.get("ID").asText();

      // Retrieve full message body
      HttpRequest msgReq = HttpRequest.newBuilder()
          .uri(URI.create("http://localhost:8025/api/v1/message/" + id))
          .timeout(Duration.ofSeconds(5))
          .GET()
          .build();
      HttpResponse<String> msgResp = http.send(msgReq, HttpResponse.BodyHandlers.ofString());
      String body = msgResp.body();

      if (subject.contains("Verify your MEDIACHECK email") && body.contains(verifyToken)) {
        foundVerify = true;
      }
      if (subject.contains("MEDIACHECK company invitation") && body.contains(inviteToken)) {
        foundInvite = true;
      }
      if (subject.contains("Reset your MEDIACHECK password") && body.contains(resetToken)) {
        foundReset = true;
      }
    }

    assertThat(foundVerify).as("Verification email captured in Mailpit").isTrue();
    assertThat(foundInvite).as("Invitation email captured in Mailpit").isTrue();
    assertThat(foundReset).as("Password reset email captured in Mailpit").isTrue();
  }
}
