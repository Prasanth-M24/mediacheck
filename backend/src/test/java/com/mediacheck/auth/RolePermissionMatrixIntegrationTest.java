package com.mediacheck.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "mediacheck.security.dev-token-delivery=true")
class RolePermissionMatrixIntegrationTest {
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

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  private String ownerToken;
  private String adminToken;
  private String reviewerToken;
  private String memberToken;
  private String companyId;
  private String ownerMembershipId;
  private String adminMembershipId;
  private String reviewerMembershipId;
  private String memberMembershipId;

  // Separate Company B
  private String companyBId;
  private String ownerBToken;

  @BeforeEach
  void setUpRoles() throws Exception {
    // Register Company A and setup OWNER
    String ownerEmail = "matrix-owner-" + UUID.randomUUID() + "@example.test";
    JsonNode regA = register(ownerEmail, "Matrix Company");
    verify(regA.get("developmentVerificationToken").asText());
    JsonNode loginOwner = login(ownerEmail, "StrongPassword!234");
    ownerToken = loginOwner.get("accessToken").asText();
    companyId = loginOwner.get("activeCompany").get("id").asText();
    ownerMembershipId = loginOwner.get("activeCompany").get("membershipId").asText();

    // Invite and onboard ADMIN
    adminToken = inviteAndOnboard(ownerToken, "ADMIN", "matrix-admin-" + UUID.randomUUID() + "@example.test");
    // Invite and onboard REVIEWER
    reviewerToken = inviteAndOnboard(ownerToken, "REVIEWER", "matrix-reviewer-" + UUID.randomUUID() + "@example.test");
    // Invite and onboard MEMBER
    memberToken = inviteAndOnboard(ownerToken, "MEMBER", "matrix-member-" + UUID.randomUUID() + "@example.test");

    // Retrieve membership IDs
    JsonNode members = json.readTree(mvc.perform(get("/api/v1/company/members").header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    for (JsonNode m : members) {
      String role = m.get("role").asText();
      if ("ADMIN".equals(role)) adminMembershipId = m.get("membershipId").asText();
      else if ("REVIEWER".equals(role)) reviewerMembershipId = m.get("membershipId").asText();
      else if ("MEMBER".equals(role)) memberMembershipId = m.get("membershipId").asText();
    }

    // Register Company B with its own OWNER
    String ownerBEmail = "matrix-owner-b-" + UUID.randomUUID() + "@example.test";
    JsonNode regB = register(ownerBEmail, "Matrix Company B");
    verify(regB.get("developmentVerificationToken").asText());
    JsonNode loginB = login(ownerBEmail, "StrongPassword!234");
    ownerBToken = loginB.get("accessToken").asText();
    companyBId = loginB.get("activeCompany").get("id").asText();
  }

  // --- Profile Access ---
  @Test
  void profilePermissionsEnforcedByRole() throws Exception {
    // All members can read profile
    mvc.perform(get("/api/v1/company/profile").header("Authorization", "Bearer " + ownerToken)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/company/profile").header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/company/profile").header("Authorization", "Bearer " + reviewerToken)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/company/profile").header("Authorization", "Bearer " + memberToken)).andExpect(status().isOk());

    // Only OWNER can update profile
    byte[] updateBody = json.writeValueAsBytes(Map.of(
        "name", "Updated Matrix Company",
        "businessEmail", "contact-" + UUID.randomUUID() + "@example.test"
    ));
    mvc.perform(put("/api/v1/company/profile").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization", "Bearer " + ownerToken).contentType(MediaType.APPLICATION_JSON).content(updateBody))
        .andExpect(status().isOk());

    mvc.perform(put("/api/v1/company/profile").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(updateBody))
        .andExpect(status().isForbidden());

    mvc.perform(put("/api/v1/company/profile").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization", "Bearer " + reviewerToken).contentType(MediaType.APPLICATION_JSON).content(updateBody))
        .andExpect(status().isForbidden());

    mvc.perform(put("/api/v1/company/profile").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization", "Bearer " + memberToken).contentType(MediaType.APPLICATION_JSON).content(updateBody))
        .andExpect(status().isForbidden());
  }

  // --- Members List Access ---
  @Test
  void membersListPermissionsEnforcedByRole() throws Exception {
    // OWNER, ADMIN, REVIEWER can view members
    mvc.perform(get("/api/v1/company/members").header("Authorization", "Bearer " + ownerToken)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/company/members").header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/company/members").header("Authorization", "Bearer " + reviewerToken)).andExpect(status().isOk());

    // MEMBER is forbidden from viewing members list
    mvc.perform(get("/api/v1/company/members").header("Authorization", "Bearer " + memberToken)).andExpect(status().isForbidden());
  }

  // --- Invitations Access ---
  @Test
  void invitationPermissionsEnforcedByRole() throws Exception {
    // OWNER, ADMIN can view invitations
    mvc.perform(get("/api/v1/company/invitations").header("Authorization", "Bearer " + ownerToken)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/company/invitations").header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());

    // REVIEWER and MEMBER cannot view invitations
    mvc.perform(get("/api/v1/company/invitations").header("Authorization", "Bearer " + reviewerToken)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/company/invitations").header("Authorization", "Bearer " + memberToken)).andExpect(status().isForbidden());

    // OWNER can invite ADMIN, REVIEWER, MEMBER
    inviteExpectStatus(ownerToken, "ADMIN", 200);
    inviteExpectStatus(ownerToken, "REVIEWER", 200);
    inviteExpectStatus(ownerToken, "MEMBER", 200);
    // OWNER cannot invite OWNER directly (server rejects OWNER creation via invite)
    inviteExpectStatus(ownerToken, "OWNER", 400);

    // ADMIN can invite REVIEWER, MEMBER
    inviteExpectStatus(adminToken, "REVIEWER", 200);
    inviteExpectStatus(adminToken, "MEMBER", 200);
    // ADMIN cannot invite ADMIN (requires OWNER)
    inviteExpectStatus(adminToken, "ADMIN", 403);
    // ADMIN cannot invite OWNER
    inviteExpectStatus(adminToken, "OWNER", 400);

    // REVIEWER and MEMBER cannot invite anyone
    inviteExpectStatus(reviewerToken, "MEMBER", 403);
    inviteExpectStatus(memberToken, "MEMBER", 403);
  }

  // --- Role Changes and Last Owner Protection ---
  @Test
  void roleChangeRestrictionsAndLastOwnerProtection() throws Exception {
    // ADMIN cannot promote MEMBER to OWNER
    mvc.perform(patch("/api/v1/company/members/" + memberMembershipId + "/role").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(Map.of("role", "OWNER"))))
        .andExpect(status().isForbidden());

    // ADMIN cannot promote MEMBER to ADMIN
    mvc.perform(patch("/api/v1/company/members/" + memberMembershipId + "/role").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(Map.of("role", "ADMIN"))))
        .andExpect(status().isForbidden());

    // ADMIN can change MEMBER to REVIEWER
    mvc.perform(patch("/api/v1/company/members/" + memberMembershipId + "/role").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(Map.of("role", "REVIEWER"))))
        .andExpect(status().isOk());

    // REVIEWER cannot change roles
    mvc.perform(patch("/api/v1/company/members/" + memberMembershipId + "/role").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization", "Bearer " + reviewerToken).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(Map.of("role", "MEMBER"))))
        .andExpect(status().isForbidden());

    // Demoting the last OWNER returns 409 Conflict
    mvc.perform(patch("/api/v1/company/members/" + ownerMembershipId + "/role").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization", "Bearer " + ownerToken).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(Map.of("role", "ADMIN"))))
        .andExpect(status().isConflict());

    // Revoking the status of the last OWNER returns 409 Conflict
    mvc.perform(patch("/api/v1/company/members/" + ownerMembershipId + "/status").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization", "Bearer " + ownerToken).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(Map.of("status", "REVOKED"))))
        .andExpect(status().isConflict());

    // Revoking a member by ADMIN succeeds
    mvc.perform(patch("/api/v1/company/members/" + reviewerMembershipId + "/status").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(Map.of("status", "REVOKED"))))
        .andExpect(status().isOk());

    // Revoked reviewer's old access token is now rejected on protected APIs
    mvc.perform(get("/api/v1/company/profile").header("Authorization", "Bearer " + reviewerToken))
        .andExpect(status().isUnauthorized());
  }

  // --- Cross-Company Tenant Isolation ---
  @Test
  void crossCompanyTenantIsolationEnforced() throws Exception {
    // User from Company B cannot inspect Company A members
    JsonNode membersB = json.readTree(mvc.perform(get("/api/v1/company/members")
        .header("Authorization", "Bearer " + ownerBToken))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    for (JsonNode m : membersB) {
      assertThat(m.get("membershipId").asText()).isNotEqualTo(ownerMembershipId);
      assertThat(m.get("membershipId").asText()).isNotEqualTo(adminMembershipId);
    }

    // User from Company B cannot modify Company A membership
    mvc.perform(patch("/api/v1/company/members/" + memberMembershipId + "/role").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization", "Bearer " + ownerBToken).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(Map.of("role", "REVIEWER"))))
        .andExpect(status().isNotFound()); // Scoped lookup does not find membership in Company B

    // User from Company B cannot switch to Company A without membership
    mvc.perform(post("/api/v1/auth/switch-company").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization", "Bearer " + ownerBToken).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(Map.of("companyId", companyId))))
        .andExpect(status().isUnauthorized());
  }

  private void inviteExpectStatus(String token, String role, int expectedStatus) throws Exception {
    mvc.perform(post("/api/v1/company/invitations").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(Map.of("email", "test-" + UUID.randomUUID() + "@example.test", "role", role))))
        .andExpect(status().is(expectedStatus));
  }

  private String inviteAndOnboard(String actorToken, String role, String email) throws Exception {
    var invResp = mvc.perform(post("/api/v1/company/invitations").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization", "Bearer " + actorToken).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(Map.of("email", email, "role", role))))
        .andExpect(status().isOk()).andReturn();
    JsonNode inv = json.readTree(invResp.getResponse().getContentAsByteArray());
    String rawToken = inv.get("developmentInvitationToken").asText();

    mvc.perform(post("/api/v1/auth/accept-invitation").with(SecurityMockMvcRequestPostProcessors.csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(Map.of("token", rawToken, "fullName", "Member " + role, "password", "StrongPassword!234", "passwordConfirmation", "StrongPassword!234"))))
        .andExpect(status().isOk());

    JsonNode loginResp = login(email, "StrongPassword!234");
    return loginResp.get("accessToken").asText();
  }

  private JsonNode register(String email, String companyName) throws Exception {
    var res = mvc.perform(post("/api/v1/auth/register-company").with(SecurityMockMvcRequestPostProcessors.csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(Map.of(
            "companyName", companyName,
            "businessEmail", "biz-" + email,
            "administratorFullName", "Admin " + email,
            "administratorEmail", email,
            "password", "StrongPassword!234",
            "passwordConfirmation", "StrongPassword!234"))))
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
    var res = mvc.perform(post("/api/v1/auth/login").with(SecurityMockMvcRequestPostProcessors.csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(Map.of("email", email, "password", password))))
        .andExpect(status().isOk()).andReturn();
    return json.readTree(res.getResponse().getContentAsByteArray());
  }
}
