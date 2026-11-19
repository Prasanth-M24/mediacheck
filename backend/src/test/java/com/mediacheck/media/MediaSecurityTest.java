package com.mediacheck.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "mediacheck.security.dev-token-delivery=true",
    "mediacheck.messaging.enabled=false"
})
public class MediaSecurityTest {

  @DynamicPropertySource
  static void isolatedDatabaseProperties(DynamicPropertyRegistry registry) {
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

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;

  private String tokenCompanyA;
  private String tokenCompanyB;

  @BeforeEach
  void setUpCompanies() throws Exception {
    String emailA = "sec-owner-a-" + UUID.randomUUID() + "@example.test";
    JsonNode regA = register(emailA, "Security Studio A");
    verify(regA.get("developmentVerificationToken").asText());
    JsonNode loginA = login(emailA, "StrongPassword!234");
    tokenCompanyA = loginA.get("accessToken").asText();

    String emailB = "sec-owner-b-" + UUID.randomUUID() + "@example.test";
    JsonNode regB = register(emailB, "Security Studio B");
    verify(regB.get("developmentVerificationToken").asText());
    JsonNode loginB = login(emailB, "StrongPassword!234");
    tokenCompanyB = loginB.get("accessToken").asText();
  }

  @Test
  void crossCompanyMediaIsolationAndIdorRejection() throws Exception {
    byte[] imageBytes = createTestPng();
    MockMultipartFile file = new MockMultipartFile("file", "secure-photo.png", "image/png", imageBytes);

    // 1. Company A uploads an image
    String uploadResp = mvc.perform(multipart("/api/v1/media")
            .file(file)
            .param("intendedChannel", "WEB")
            .param("intendedTerritory", "US")
            .param("intendedUse", "COMMERCIAL")
            .with(SecurityMockMvcRequestPostProcessors.csrf())
            .header("Authorization", "Bearer " + tokenCompanyA))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    JsonNode mediaObj = json.readTree(uploadResp);
    String mediaId = mediaObj.get("id").asText();
    assertThat(mediaId).isNotBlank();

    // 2. Company A can view its own media detail
    mvc.perform(get("/api/v1/media/" + mediaId)
            .header("Authorization", "Bearer " + tokenCompanyA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.originalFilename").value("secure-photo.png"));

    // 3. Company A can download its own file
    mvc.perform(get("/api/v1/media/" + mediaId + "/file")
            .header("Authorization", "Bearer " + tokenCompanyA))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "inline"));

    // 4. Company A can view its media report
    mvc.perform(get("/api/v1/media/" + mediaId + "/report")
            .header("Authorization", "Bearer " + tokenCompanyA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decision").exists());

    // 5. Cross-Company IDOR: Company B attempts to access Company A's media -> 404 NOT FOUND
    mvc.perform(get("/api/v1/media/" + mediaId)
            .header("Authorization", "Bearer " + tokenCompanyB))
        .andExpect(status().isNotFound());

    // 6. Cross-Company IDOR: Company B attempts to download Company A's file -> 404 NOT FOUND
    mvc.perform(get("/api/v1/media/" + mediaId + "/file")
            .header("Authorization", "Bearer " + tokenCompanyB))
        .andExpect(status().isNotFound());

    // 7. Cross-Company IDOR: Company B attempts to access Company A's report -> 404 NOT FOUND
    mvc.perform(get("/api/v1/media/" + mediaId + "/report")
            .header("Authorization", "Bearer " + tokenCompanyB))
        .andExpect(status().isNotFound());

    // 8. Cross-Company IDOR: Company B attempts to archive Company A's media -> 404 NOT FOUND
    mvc.perform(delete("/api/v1/media/" + mediaId)
            .with(SecurityMockMvcRequestPostProcessors.csrf())
            .header("Authorization", "Bearer " + tokenCompanyB))
        .andExpect(status().isNotFound());

    // 9. Media library for Company B does NOT contain Company A's asset
    String listResp = mvc.perform(get("/api/v1/media")
            .header("Authorization", "Bearer " + tokenCompanyB))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    JsonNode pagedMedia = json.readTree(listResp);
    assertThat(pagedMedia.get("totalElements").asInt()).isEqualTo(0);

    // 10. Unauthenticated access is rejected with 401
    mvc.perform(get("/api/v1/media/" + mediaId))
        .andExpect(status().isUnauthorized());
  }

  private JsonNode register(String email, String companyName) throws Exception {
    byte[] body = json.writeValueAsBytes(Map.of(
        "companyName", companyName,
        "businessEmail", "business-" + email,
        "administratorFullName", "Initial Owner",
        "administratorEmail", email,
        "password", "StrongPassword!234",
        "passwordConfirmation", "StrongPassword!234"
    ));
    var res = mvc.perform(post("/api/v1/auth/register-company").with(SecurityMockMvcRequestPostProcessors.csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isAccepted()).andReturn();
    return json.readTree(res.getResponse().getContentAsByteArray());
  }

  private void verify(String token) throws Exception {
    mvc.perform(post("/api/v1/auth/verify-email").with(SecurityMockMvcRequestPostProcessors.csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of("token", token))))
        .andExpect(status().isOk());
  }

  private JsonNode login(String email, String password) throws Exception {
    byte[] body = json.writeValueAsBytes(Map.of("email", email, "password", password));
    var res = mvc.perform(post("/api/v1/auth/login").with(SecurityMockMvcRequestPostProcessors.csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andReturn();
    return json.readTree(res.getResponse().getContentAsByteArray());
  }

  private byte[] createTestPng() {
    try {
      BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
      var g = img.createGraphics();
      g.setColor(Color.GREEN);
      g.fillRect(0, 0, 100, 100);
      g.dispose();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ImageIO.write(img, "png", baos);
      return baos.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
