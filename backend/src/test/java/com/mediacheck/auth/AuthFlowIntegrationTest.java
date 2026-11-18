package com.mediacheck.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediacheck.domain.PlatformUser;
import com.mediacheck.domain.AuditEvent;
import com.mediacheck.repository.AuditEventRepository;
import com.mediacheck.repository.PlatformUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties="mediacheck.security.dev-token-delivery=true")
class AuthFlowIntegrationTest {
  @DynamicPropertySource
  static void isolatedDatabaseProperties(DynamicPropertyRegistry registry) {
    String url=System.getenv("MEDIACHECK_TEST_DATABASE_URL");
    if(url!=null && !url.isBlank()) {
      registry.add("spring.datasource.url",()->url);
      registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
      registry.add("spring.datasource.username",()->System.getenv("MEDIACHECK_TEST_DATABASE_USERNAME"));
      registry.add("spring.datasource.password",()->System.getenv("MEDIACHECK_TEST_DATABASE_PASSWORD"));
      registry.add("spring.flyway.target",()->System.getenv().getOrDefault("MEDIACHECK_TEST_FLYWAY_TARGET","4"));
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired PlatformUserRepository users;
  @Autowired JwtEncoder jwtEncoder;
  @Autowired JwtDecoder jwtDecoder;
  @Autowired JdbcTemplate jdbc;
  @SpyBean AuditEventRepository auditEvents;

  @Test
  void registrationRequiresExplicitVerificationThenLoginAndRefreshRotateSession() throws Exception {
    String email="phase22-"+java.util.UUID.randomUUID()+"@example.test";
    JsonNode registered=register(email);
    PlatformUser user=users.findByEmail(email).orElseThrow();
    assertThat(user.getStatus().name()).isEqualTo("INVITED");
    assertThat(user.getPasswordHash()).isNotEqualTo("StrongPassword!234");

    mvc.perform(post("/api/v1/auth/verify-email").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("token",registered.get("developmentVerificationToken").asText()))))
        .andExpect(status().isOk());
    LoginResult login=login(email,"StrongPassword!234");
    String oldAccess=login.body().get("accessToken").asText();
    var refreshCookie=mvc.perform(post("/api/v1/auth/refresh").with(SecurityMockMvcRequestPostProcessors.csrf())
        .cookie(new MockCookie("MEDIACHECK_REFRESH",login.refreshToken())))
        .andExpect(status().isOk()).andReturn();
    JsonNode rotated=json.readTree(refreshCookie.getResponse().getContentAsByteArray());
    String newAccess=rotated.get("accessToken").asText();
    assertThat(newAccess).isNotEqualTo(oldAccess);
    mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+oldAccess)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+newAccess)).andExpect(status().isOk())
        .andExpect(jsonPath("$.activeCompany.role").value("OWNER"))
        .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    assertThat(refreshCookie.getResponse().getCookie("MEDIACHECK_REFRESH")).isNotNull();
    mvc.perform(post("/api/v1/auth/refresh").with(SecurityMockMvcRequestPostProcessors.csrf())
        .cookie(new MockCookie("MEDIACHECK_REFRESH",login.refreshToken()))).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+newAccess)).andExpect(status().isUnauthorized());
  }

  @Test
  void duplicateRegistrationIsRejectedAndProtectedEndpointsRequireAuthentication() throws Exception {
    String email="duplicate-"+java.util.UUID.randomUUID()+"@example.test";
    register(email);
    mvc.perform(post("/api/v1/auth/register-company").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON).content(registrationJson(email)))
        .andExpect(status().isAccepted());
    mvc.perform(get("/api/v1/company/members")).andExpect(status().isUnauthorized());
  }

  @Test
  void registrationDatabaseFailureRollsBackAllRowsAndEmailComparisonIsCaseInsensitive() throws Exception {
    String email="rollback-"+java.util.UUID.randomUUID()+"@example.test";
    byte[] invalid=json.writeValueAsBytes(java.util.Map.of("companyName","Rollback Studio","businessEmail","rollback-business-"+email,
        "administratorFullName","Initial Owner","administratorEmail",email,"password","StrongPassword!234","passwordConfirmation","different"));
    mvc.perform(post("/api/v1/auth/register-company").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON).content(invalid))
        .andExpect(status().isBadRequest());
    assertThat(users.findByEmail(email)).isEmpty();
    assertThat(jdbc.queryForObject("select count(*) from companies where lower(business_email)=lower(?)",Integer.class,"rollback-business-"+email)).isZero();
    String goodEmail="normalized-"+java.util.UUID.randomUUID()+"@example.test";
    register(goodEmail);
    mvc.perform(post("/api/v1/auth/register-company").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(registrationJson(goodEmail))).andExpect(status().isAccepted())
        .andExpect(jsonPath("$.userId").doesNotExist()).andExpect(jsonPath("$.companyId").doesNotExist());
    String otherAdmin="business-duplicate-"+java.util.UUID.randomUUID()+"@example.test";
    var businessDuplicate=new java.util.HashMap<String,Object>();businessDuplicate.put("companyName","Another Studio");
    businessDuplicate.put("businessEmail","business-"+goodEmail);businessDuplicate.put("administratorFullName","Another Owner");
    businessDuplicate.put("administratorEmail",otherAdmin);businessDuplicate.put("password","StrongPassword!234");businessDuplicate.put("passwordConfirmation","StrongPassword!234");
    mvc.perform(post("/api/v1/auth/register-company").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(businessDuplicate))).andExpect(status().isAccepted())
        .andExpect(jsonPath("$.userId").doesNotExist()).andExpect(jsonPath("$.companyId").doesNotExist());
  }

  @Test
  void registrationRollsBackWhenAuditPersistenceFails() throws Exception {
    String email="atomic-"+java.util.UUID.randomUUID()+"@example.test";
    doThrow(new org.springframework.dao.DataIntegrityViolationException("simulated audit persistence failure"))
        .when(auditEvents).save(any(AuditEvent.class));
    try {
      mvc.perform(post("/api/v1/auth/register-company").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
          .content(registrationJson(email))).andExpect(status().isConflict());
    } finally {
      reset(auditEvents);
    }
    assertThat(users.findByEmail(email)).isEmpty();
    assertThat(jdbc.queryForObject("select count(*) from companies where lower(business_email)=lower(?)",Integer.class,"business-"+email)).isZero();
  }

  @Test
  void invalidCredentialsAreGenericAndPasswordFailuresDoNotExposeAccountState() throws Exception {
    String email="invalid-"+java.util.UUID.randomUUID()+"@example.test";
    register(email);
    mvc.perform(post("/api/v1/auth/login").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("email",email,"password","WrongPassword!234"))))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("invalid_credentials"));
    mvc.perform(post("/api/v1/auth/login").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("email","missing@example.test","password","WrongPassword!234"))))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("invalid_credentials"));
  }

  @Test
  void repeatedFailuresSuspendAccountAndPasswordResetUnlocksIt() throws Exception {
    String email="locked-"+java.util.UUID.randomUUID()+"@example.test";
    JsonNode registered=register(email);verify(registered.get("developmentVerificationToken").asText());
    for(int i=0;i<5;i++) mvc.perform(post("/api/v1/auth/login").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("email",email,"password","WrongPassword!234")))).andExpect(status().isUnauthorized());
    PlatformUser suspended=users.findByEmail(email).orElseThrow();
    assertThat(suspended.getStatus().name()).isEqualTo("SUSPENDED");
    assertThat(suspended.getLockedUntil()).isNotNull();
    mvc.perform(post("/api/v1/auth/login").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("email",email,"password","StrongPassword!234")))).andExpect(status().isUnauthorized());
    JsonNode reset=json.readTree(mvc.perform(post("/api/v1/auth/forgot-password").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("email",email)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    mvc.perform(post("/api/v1/auth/reset-password").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("token",reset.get("developmentResetToken").asText(),"password","ResetPassword!567","passwordConfirmation","ResetPassword!567"))))
        .andExpect(status().isOk());
    login(email,"ResetPassword!567");
  }

  @Test
  void expiredLockoutRestoresLoginAndClearCsrfIsRejected() throws Exception {
    String email="expiry-"+java.util.UUID.randomUUID()+"@example.test";
    JsonNode registered=register(email);verify(registered.get("developmentVerificationToken").asText());
    for(int i=0;i<5;i++) mvc.perform(post("/api/v1/auth/login").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("email",email,"password","WrongPassword!234")))).andExpect(status().isUnauthorized());
    PlatformUser user=users.findByEmail(email).orElseThrow();
    user.setLockedUntil(java.time.Instant.now().minusSeconds(2)); users.saveAndFlush(user);
    login(email,"StrongPassword!234");
    mvc.perform(post("/api/v1/auth/logout-all").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/auth/register-company").contentType(MediaType.APPLICATION_JSON).content(registrationJson("csrf-"+java.util.UUID.randomUUID()+"@example.test")))
        .andExpect(status().isForbidden());
  }

  @Test
  void administrativeSuspensionIsNotClearedByExpiredLockTimestamp() {
    PlatformUser adminSuspended=new PlatformUser("admin-suspended@example.test","Admin Suspended","$2a$12$test",com.mediacheck.domain.UserStatus.ACTIVE);
    adminSuspended.setStatus(com.mediacheck.domain.UserStatus.SUSPENDED);
    adminSuspended.setLockedUntil(java.time.Instant.now().minusSeconds(10));
    adminSuspended.expireLoginLock(java.time.Instant.now());
    adminSuspended.unlockAfterPasswordReset();
    assertThat(adminSuspended.getStatus()).isEqualTo(com.mediacheck.domain.UserStatus.SUSPENDED);
  }

  @Test
  void logoutRevokesAccessSessionAndRefreshFamily() throws Exception {
    String email="logout-"+java.util.UUID.randomUUID()+"@example.test";
    JsonNode registered=register(email);verify(registered.get("developmentVerificationToken").asText());
    LoginResult login=login(email,"StrongPassword!234");
    mvc.perform(post("/api/v1/auth/logout").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization","Bearer "+login.body().get("accessToken").asText()).cookie(new MockCookie("MEDIACHECK_REFRESH",login.refreshToken())))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+login.body().get("accessToken").asText())).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/auth/refresh").with(SecurityMockMvcRequestPostProcessors.csrf()).cookie(new MockCookie("MEDIACHECK_REFRESH",login.refreshToken())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @EnabledIfEnvironmentVariable(named="MEDIACHECK_TEST_DATABASE_URL", matches="jdbc:postgresql:.*")
  void simultaneousRefreshReuseRevokesBothCompetingSessions() throws Exception {
    String email="refresh-race-"+java.util.UUID.randomUUID()+"@example.test";
    JsonNode registration=register(email); verify(registration.get("developmentVerificationToken").asText());
    LoginResult login=login(email,"StrongPassword!234");
    var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
    try(var pool=Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<Integer> request=()->{
        ready.countDown();
        if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("refresh race did not start");
        return mvc.perform(post("/api/v1/auth/refresh").with(SecurityMockMvcRequestPostProcessors.csrf())
            .cookie(new MockCookie("MEDIACHECK_REFRESH",login.refreshToken())))
            .andReturn().getResponse().getStatus();
      };
      var first=pool.submit(request); var second=pool.submit(request);
      assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
      int firstStatus=first.get(30,TimeUnit.SECONDS), secondStatus=second.get(30,TimeUnit.SECONDS);
      assertThat(java.util.List.of(firstStatus,secondStatus)).containsExactlyInAnyOrder(200,401);
    }
    var sessions=jdbc.queryForList("select revoked_at from refresh_sessions where user_id=(select id from users where email=?)",java.sql.Timestamp.class,email);
    assertThat(sessions).hasSize(2).allMatch(java.util.Objects::nonNull);
  }

  @Test
  @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="MEDIACHECK_TEST_DATABASE_URL", matches="jdbc:postgresql:.*")
  void registryIsCompanyScopedAndPersistsFileIntegrityMetadata() throws Exception {
    String ownerEmail="registry-owner-"+java.util.UUID.randomUUID()+"@example.test";
    JsonNode registration=register(ownerEmail); verify(registration.get("developmentVerificationToken").asText());
    LoginResult owner=login(ownerEmail,"StrongPassword!234");
    var created=mvc.perform(post("/api/v1/rights-assets").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization","Bearer "+owner.body().get("accessToken").asText()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("title","Registry Image","assetType","IMAGE","rightsHolder","Owner"))))
        .andExpect(status().isOk()).andReturn();
    JsonNode asset=json.readTree(created.getResponse().getContentAsByteArray()); String assetId=asset.get("id").asText();
    mvc.perform(multipart("/api/v1/rights-assets/"+assetId+"/file").file(new MockMultipartFile("file","asset.png","image/png","registry-data".getBytes()))
        .with(SecurityMockMvcRequestPostProcessors.csrf()).header("Authorization","Bearer "+owner.body().get("accessToken").asText()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.hasFile").value(true)).andExpect(jsonPath("$.sha256Checksum").isNotEmpty());
    var licence=mvc.perform(post("/api/v1/licences").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization","Bearer "+owner.body().get("accessToken").asText()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("licenceReference","LIC-1","name","Registry Licence","licensorName","Licensor","status","ACTIVE"))))
        .andExpect(status().isOk()).andReturn();
    String licenceId=json.readTree(licence.getResponse().getContentAsByteArray()).get("id").asText();
    mvc.perform(multipart("/api/v1/licences/"+licenceId+"/documents").file(new MockMultipartFile("file","licence.pdf","application/pdf","document".getBytes()))
        .with(SecurityMockMvcRequestPostProcessors.csrf()).header("Authorization","Bearer "+owner.body().get("accessToken").asText()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.sha256Checksum").isNotEmpty());
    mvc.perform(get("/api/v1/rights-registry/dashboard").header("Authorization","Bearer "+owner.body().get("accessToken").asText()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalAssets").value(1)).andExpect(jsonPath("$.activeLicences").value(1));
    mvc.perform(get("/api/v1/rights-assets").header("Authorization","Bearer "+owner.body().get("accessToken").asText()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get("/api/v1/licences").header("Authorization","Bearer "+owner.body().get("accessToken").asText()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));

    String otherEmail="registry-other-"+java.util.UUID.randomUUID()+"@example.test";
    JsonNode otherRegistration=register(otherEmail); verify(otherRegistration.get("developmentVerificationToken").asText()); LoginResult other=login(otherEmail,"StrongPassword!234");
    mvc.perform(get("/api/v1/rights-assets/"+assetId).header("Authorization","Bearer "+other.body().get("accessToken").asText())).andExpect(status().isNotFound());
  }

  @Test
  void invitationAndCompanySelectionEnforceLiveTenantMembership() throws Exception {
    String ownerEmail="owner-"+java.util.UUID.randomUUID()+"@example.test";
    JsonNode ownerRegistration=register(ownerEmail);
    verify(ownerRegistration.get("developmentVerificationToken").asText());
    LoginResult owner=login(ownerEmail,"StrongPassword!234");
    LoginResult firstSession=login(ownerEmail,"StrongPassword!234");
    String ownerCompany=owner.body().get("activeCompany").get("id").asText();
    var invited=mvc.perform(post("/api/v1/company/invitations").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization","Bearer "+owner.body().get("accessToken").asText()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("email","reviewer-"+java.util.UUID.randomUUID()+"@example.test","role","REVIEWER"))))
        .andExpect(status().isOk()).andReturn();
    JsonNode invite=json.readTree(invited.getResponse().getContentAsByteArray());
    String reviewerEmail=invite.get("email").asText();
    mvc.perform(post("/api/v1/auth/accept-invitation").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("token",invite.get("developmentInvitationToken").asText(),"fullName","Reviewer One","password","ReviewerPassword!234","passwordConfirmation","ReviewerPassword!234"))))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/auth/accept-invitation").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("token",invite.get("developmentInvitationToken").asText(),"fullName","Replay User","password","ReviewerPassword!234","passwordConfirmation","ReviewerPassword!234"))))
        .andExpect(status().isBadRequest());
    LoginResult reviewer=login(reviewerEmail,"ReviewerPassword!234");
    mvc.perform(get("/api/v1/company/members").header("Authorization","Bearer "+reviewer.body().get("accessToken").asText()))
        .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.email=='"+ownerEmail+"')]").isNotEmpty());
    mvc.perform(get("/api/v1/company/invitations").header("Authorization","Bearer "+reviewer.body().get("accessToken").asText())).andExpect(status().isForbidden());
    mvc.perform(put("/api/v1/company/profile").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization","Bearer "+reviewer.body().get("accessToken").asText()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("name","Forged","businessEmail","forged@example.test"))))
        .andExpect(status().isForbidden());

    String companyBEmail="owner-b-"+java.util.UUID.randomUUID()+"@example.test";
    JsonNode registrationB=register(companyBEmail);verify(registrationB.get("developmentVerificationToken").asText());
    LoginResult ownerB=login(companyBEmail,"StrongPassword!234");
    String companyB=ownerB.body().get("activeCompany").get("id").asText();
    var switched=mvc.perform(post("/api/v1/auth/switch-company").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization","Bearer "+firstSession.body().get("accessToken").asText())
        .cookie(new MockCookie("MEDIACHECK_REFRESH",firstSession.refreshToken())).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("companyId",ownerCompany))))
        .andExpect(status().isOk()).andReturn();
    JsonNode switchedToken=json.readTree(switched.getResponse().getContentAsByteArray());
    mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+firstSession.body().get("accessToken").asText())).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+switchedToken.get("accessToken").asText())).andExpect(status().isOk())
        .andExpect(jsonPath("$.activeCompany.id").value(ownerCompany));

    JsonNode members=json.readTree(mvc.perform(get("/api/v1/company/members").header("Authorization","Bearer "+owner.body().get("accessToken").asText()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    assertThat(members.toString()).doesNotContain(companyBEmail);
    String membershipId=null;
    for(JsonNode member:members) if(member.get("email").asText().equals(reviewerEmail)) membershipId=member.get("membershipId").asText();
    mvc.perform(patch("/api/v1/company/members/"+membershipId+"/status").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization","Bearer "+owner.body().get("accessToken").asText()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("status","REVOKED"))))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+reviewer.body().get("accessToken").asText())).andExpect(status().isUnauthorized());
    assertThat(ownerCompany).isNotEqualTo(companyB);
  }

  @Test
  void expiredInvitationCannotBeAccepted() throws Exception {
    String ownerEmail="invite-expiry-"+java.util.UUID.randomUUID()+"@example.test";
    JsonNode registered=register(ownerEmail);verify(registered.get("developmentVerificationToken").asText());
    LoginResult owner=login(ownerEmail,"StrongPassword!234");
    var response=mvc.perform(post("/api/v1/company/invitations").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization","Bearer "+owner.body().get("accessToken").asText()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("email","expires-"+java.util.UUID.randomUUID()+"@example.test","role","MEMBER"))))
        .andExpect(status().isOk()).andReturn();
    JsonNode invitation=json.readTree(response.getResponse().getContentAsByteArray());
    java.time.Instant expiredCreated=java.time.Instant.now().minusSeconds(3600);
    jdbc.update("update company_invitations set created_at=?,expires_at=? where id=?",
        java.sql.Timestamp.from(expiredCreated),
        java.sql.Timestamp.from(expiredCreated.plusSeconds(60)),
        java.util.UUID.fromString(invitation.get("id").asText()));
    mvc.perform(post("/api/v1/auth/accept-invitation").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("token",invitation.get("developmentInvitationToken").asText(),"fullName","Expired User","password","ReviewerPassword!234","passwordConfirmation","ReviewerPassword!234"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void jwtDecoderRejectsTamperingWrongIssuerAndExpiredTokens() {
    var now=java.time.Instant.now();
    var valid=jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder()
        .issuer("http://localhost:8080").subject(java.util.UUID.randomUUID().toString()).issuedAt(now).expiresAt(now.plusSeconds(60)).build())).getTokenValue();
    assertThat(jwtDecoder.decode(valid).getSubject()).isNotBlank();
    String[] parts=valid.split("\\.");parts[2]=(parts[2].startsWith("A")?"B":"A")+parts[2].substring(1);
    org.assertj.core.api.Assertions.assertThatThrownBy(()->jwtDecoder.decode(String.join(".",parts))).isInstanceOf(RuntimeException.class);
    var wrongIssuer=jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder()
        .issuer("https://wrong.example").subject("user").issuedAt(now).expiresAt(now.plusSeconds(60)).build())).getTokenValue();
    org.assertj.core.api.Assertions.assertThatThrownBy(()->jwtDecoder.decode(wrongIssuer)).isInstanceOf(RuntimeException.class);
    var expired=jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder()
        .issuer("http://localhost:8080").subject("user").issuedAt(now.minusSeconds(120)).expiresAt(now.minusSeconds(60)).build())).getTokenValue();
    org.assertj.core.api.Assertions.assertThatThrownBy(()->jwtDecoder.decode(expired)).isInstanceOf(RuntimeException.class);
  }

  @Test
  void jwtWithUnsecuredNoneOrWrongAlgorithmIsRejected() throws Exception {
    String header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"test\",\"iss\":\"http://localhost:8080\",\"exp\":9999999999}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    String noneToken = header + "." + payload + ".";

    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + noneToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void clientSideRoleEscalationAndCompanyIdInjectionAreIgnored() throws Exception {
    String email = "escalate-" + java.util.UUID.randomUUID() + "@example.test";
    var body = new java.util.HashMap<String, Object>();
    body.put("companyName", "Escalation Studio");
    body.put("businessEmail", "biz-" + email);
    body.put("administratorFullName", "Attacker");
    body.put("administratorEmail", email);
    body.put("password", "StrongPassword!234");
    body.put("passwordConfirmation", "StrongPassword!234");
    body.put("role", "SUPERUSER");
    body.put("companyId", java.util.UUID.randomUUID().toString());
    body.put("status", "ACTIVE");

    var res = mvc.perform(post("/api/v1/auth/register-company").with(SecurityMockMvcRequestPostProcessors.csrf())
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
        .andExpect(status().isAccepted()).andReturn();
    JsonNode respJson = json.readTree(res.getResponse().getContentAsByteArray());
    verify(respJson.get("developmentVerificationToken").asText());

    LoginResult login = login(email, "StrongPassword!234");
    assertThat(login.body().get("activeCompany").get("role").asText()).isEqualTo("OWNER");
    assertThat(login.body().get("activeCompany").get("id").asText()).isNotEqualTo(body.get("companyId"));
  }

  @Test
  void concurrentInvitationAcceptancePermitsOnlyOneWinner() throws Exception {
    String ownerEmail = "invite-race-owner-" + java.util.UUID.randomUUID() + "@example.test";
    JsonNode reg = register(ownerEmail);
    verify(reg.get("developmentVerificationToken").asText());
    LoginResult owner = login(ownerEmail, "StrongPassword!234");

    var invited = mvc.perform(post("/api/v1/company/invitations").with(SecurityMockMvcRequestPostProcessors.csrf())
        .header("Authorization", "Bearer " + owner.body().get("accessToken").asText()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("email", "race-invite-" + java.util.UUID.randomUUID() + "@example.test", "role", "REVIEWER"))))
        .andExpect(status().isOk()).andReturn();
    JsonNode invite = json.readTree(invited.getResponse().getContentAsByteArray());
    String rawToken = invite.get("developmentInvitationToken").asText();

    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<Integer> accept = () -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("race did not start");
        return mvc.perform(post("/api/v1/auth/accept-invitation").with(SecurityMockMvcRequestPostProcessors.csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(java.util.Map.of(
                "token", rawToken,
                "fullName", "Racer",
                "password", "RacePassword!234",
                "passwordConfirmation", "RacePassword!234"
            ))))
            .andReturn().getResponse().getStatus();
      };
      var f1 = pool.submit(accept);
      var f2 = pool.submit(accept);
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      int s1 = f1.get(30, TimeUnit.SECONDS), s2 = f2.get(30, TimeUnit.SECONDS);
      assertThat(java.util.List.of(s1, s2)).containsExactlyInAnyOrder(200, 400);
    }
  }

  private JsonNode register(String email) throws Exception {
    var result=mvc.perform(post("/api/v1/auth/register-company").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON).content(registrationJson(email)))
        .andExpect(status().isAccepted()).andReturn();
    return json.readTree(result.getResponse().getContentAsByteArray());
  }
  private void verify(String token) throws Exception {mvc.perform(post("/api/v1/auth/verify-email").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(java.util.Map.of("token",token)))).andExpect(status().isOk());}
  private byte[] registrationJson(String email) throws Exception {
    return json.writeValueAsBytes(java.util.Map.of("companyName","Secure Studio","businessEmail","business-"+email,
        "administratorFullName","Initial Owner","administratorEmail",email,"password","StrongPassword!234","passwordConfirmation","StrongPassword!234"));
  }
  private LoginResult login(String email,String password) throws Exception {
    var result=mvc.perform(post("/api/v1/auth/login").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(java.util.Map.of("email",email,"password",password))))
        .andExpect(status().isOk()).andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("HttpOnly"))).andReturn();
    return new LoginResult(json.readTree(result.getResponse().getContentAsByteArray()),result.getResponse().getCookie("MEDIACHECK_REFRESH").getValue());
  }
  private record LoginResult(JsonNode body,String refreshToken){}
}
