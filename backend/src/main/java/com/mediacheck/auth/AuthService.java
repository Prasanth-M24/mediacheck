package com.mediacheck.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.mediacheck.domain.*;
import com.mediacheck.repository.*;
import com.mediacheck.auth.AuthDtos.*;

@Service
public class AuthService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String DUMMY_HASH = "$2a$12$C6UzMDM.H6dfI/f/IKcEe.5rqFrtYNEz3Fv.E6wqIbK4jv7R7fN6a";
  private final CompanyRepository companies;
  private final PlatformUserRepository users;
  private final CompanyMembershipRepository memberships;
  private final RefreshSessionRepository sessions;
  private final CompanyInvitationRepository invitations;
  private final AccountActionTokenRepository actionTokens;
  private final AuditEventRepository auditEvents;
  private final AccountEmailService accountEmail;
  private final PasswordEncoder passwords;
  private final JwtEncoder jwtEncoder;
  private final String issuer;
  private final Duration accessTtl;
  private final Duration refreshTtl;
  private final boolean exposeDevTokens;
  private final boolean secureCookie;

  public AuthService(CompanyRepository companies, PlatformUserRepository users, CompanyMembershipRepository memberships,
      RefreshSessionRepository sessions, CompanyInvitationRepository invitations, AccountActionTokenRepository actionTokens,
      AuditEventRepository auditEvents, AccountEmailService accountEmail, PasswordEncoder passwords, JwtEncoder jwtEncoder,
      @Value("${mediacheck.security.issuer:http://localhost:8080}") String issuer,
      @Value("${mediacheck.security.access-token-seconds:600}") long accessSeconds,
      @Value("${mediacheck.security.refresh-token-days:14}") long refreshDays,
      @Value("${mediacheck.security.dev-token-delivery:false}") boolean exposeDevTokens,
      @Value("${mediacheck.security.refresh-cookie-secure:false}") boolean secureCookie) {
    this.companies=companies; this.users=users; this.memberships=memberships; this.sessions=sessions; this.invitations=invitations;
    this.actionTokens=actionTokens; this.auditEvents=auditEvents; this.accountEmail=accountEmail; this.passwords=passwords; this.jwtEncoder=jwtEncoder;
    this.issuer=issuer; this.accessTtl=Duration.ofSeconds(accessSeconds); this.refreshTtl=Duration.ofDays(refreshDays);
    this.exposeDevTokens=exposeDevTokens; this.secureCookie=secureCookie;
  }

  @Transactional
  public RegisterResponse registerCompany(RegisterCompanyRequest request) {
    if (!request.password().equals(request.passwordConfirmation())) throw badRequest("password_confirmation_mismatch");
    if(!strongPassword(request.password())) throw badRequest("password_policy_not_met");
    String email=normalize(request.administratorEmail()), businessEmail=normalize(request.businessEmail());
    if (users.existsByEmail(email) || companies.existsByBusinessEmailIgnoreCase(businessEmail))
      return new RegisterResponse(null,null,"PENDING","If eligible, verification instructions will be sent.",null);
    String slug=slug(request.companyName());
    Company company=new Company(request.companyName().trim(),slug);
    company.setRegistrationDetails(trimToNull(request.legalName()),businessEmail,trimToNull(request.contactNumber()),trimToNull(request.website()));
    companies.save(company);
    PlatformUser user=new PlatformUser(email,request.administratorFullName().trim(),passwords.encode(request.password()),UserStatus.INVITED);
    users.save(user);
    CompanyMembership membership=new CompanyMembership(company,user,MembershipRole.OWNER);
    memberships.save(membership);
    String verification=createActionToken(user,AccountActionToken.Purpose.VERIFY_EMAIL,Duration.ofHours(24));
    accountEmail.verification(email,verification);
    audit(company,user,membership,"COMPANY_REGISTERED",company.getId());
    return new RegisterResponse(null,null,"PENDING","If eligible, verification instructions will be sent.",exposeDevTokens?verification:null);
  }

  @Transactional(noRollbackFor = ResponseStatusException.class)
  public Issued login(LoginRequest request) {
    String email=normalize(request.email());
    PlatformUser user=users.findByEmailForLogin(email).orElse(null);
    if(user==null){passwords.matches(request.password(),DUMMY_HASH); throw invalidCredentials();}
    user.expireLoginLock(Instant.now());
    if(user.getPasswordHash()==null || !passwords.matches(request.password(),user.getPasswordHash()) || user.getStatus()!=UserStatus.ACTIVE || !user.isEmailVerified()) {
      if(user.getStatus()==UserStatus.ACTIVE) {
        user.recordFailedLogin(5,Instant.now().plus(Duration.ofMinutes(15)));
        users.saveAndFlush(user);
      }
      throw invalidCredentials();
    }
    List<CompanyMembership> active=memberships.findAllByUser_IdAndStatusAndCompany_Status(user.getId(),MembershipStatus.ACTIVE,CompanyStatus.ACTIVE);
    CompanyMembership membership;
    if(request.companyId()!=null) membership=active.stream().filter(m->m.getCompany().getId().equals(request.companyId())).findFirst().orElseThrow(AuthService::invalidCredentials);
    else if(active.size()==1) membership=active.getFirst();
    else throw new CompanySelectionRequiredException(active.stream().map(AuthService::safeCompany).toList());
    user.clearFailedLogins();
    audit(membership.getCompany(),user,membership,"LOGIN_SUCCEEDED",user.getId());
    return issue(user,membership);
  }

  @Transactional
  public Issued refresh(String rawToken) {
    if(rawToken==null || rawToken.isBlank()) return null;
    RefreshSession session=sessions.findByTokenHashForUpdate(hash(rawToken)).orElse(null);
    if(session==null) return null;
    if(session.getRevokedAt()!=null) { revokeAll(session.getUserId()); return null; }
    if(!session.getExpiresAt().isAfter(Instant.now())) { session.revoke(Instant.now(),null); return null; }
    PlatformUser user=users.findById(session.getUserId()).filter(u->u.getStatus()==UserStatus.ACTIVE && u.isEmailVerified()).orElse(null);
    CompanyMembership membership=memberships.findByIdAndCompany_IdAndUser_IdAndStatus(session.getMembershipId(),session.getCompanyId(),session.getUserId(),MembershipStatus.ACTIVE).orElse(null);
    if(user==null || membership==null) { session.revoke(Instant.now(),null); return null; }
    Issued next=issue(user,membership);
    session.revoke(Instant.now(),next.session().getId());
    audit(membership.getCompany(),user,membership,"TOKEN_REFRESHED",session.getId());
    return next;
  }

  @Transactional
  public Issued switchCompany(UUID userId,UUID companyId,String currentRefresh,UUID currentSessionId) {
    CompanyMembership membership=memberships.findByCompany_IdAndUser_IdAndStatusAndCompany_Status(companyId,userId,MembershipStatus.ACTIVE,CompanyStatus.ACTIVE).orElseThrow(AuthService::invalidCredentials);
    if(!membership.getUser().isEmailVerified() || membership.getUser().getStatus()!=UserStatus.ACTIVE) throw invalidCredentials();
    if(currentRefresh!=null) sessions.findByTokenHash(hash(currentRefresh)).filter(s->s.getUserId().equals(userId)&&s.getRevokedAt()==null).ifPresent(s->s.revoke(Instant.now(),null));
    if(currentSessionId!=null) sessions.findById(currentSessionId).filter(s->s.getUserId().equals(userId)&&s.getRevokedAt()==null).ifPresent(s->s.revoke(Instant.now(),null));
    audit(membership.getCompany(),membership.getUser(),membership,"COMPANY_CONTEXT_SWITCHED",companyId);
    return issue(membership.getUser(),membership);
  }

  @Transactional
  public boolean acceptInvitation(String rawToken,String displayName,String password,String confirmation) {
    if(rawToken==null || rawToken.isBlank()) return false;
    CompanyInvitation invite=invitations.findByTokenHashForUpdate(hash(rawToken)).orElse(null);
    Instant now=Instant.now();
    if(invite==null || invite.getAcceptedAt()!=null || invite.getRevokedAt()!=null || !invite.getExpiresAt().isAfter(now)) return false;
    PlatformUser user=users.findByEmail(normalize(invite.getEmail())).orElse(null);
    if(user==null) {
      if(displayName==null || displayName.isBlank() || !strongPassword(password) || !password.equals(confirmation)) return false;
      user=new PlatformUser(normalize(invite.getEmail()),displayName.trim(),passwords.encode(password),UserStatus.ACTIVE);
      user.setEmailVerified(true); users.save(user);
    } else {
      if(user.getStatus()==UserStatus.SUSPENDED && (user.getFailedLoginAttempts()<5 || user.getLockedUntil()==null || user.getLockedUntil().isAfter(now))) return false;
      if(user.getPasswordHash()==null) {
        if(!strongPassword(password) || !password.equals(confirmation)) return false;
        user.setPasswordHash(passwords.encode(password));
      }
      user.setEmailVerified(true);
      if(user.getStatus()==UserStatus.INVITED || user.getStatus()==UserStatus.SUSPENDED) user.setStatus(UserStatus.ACTIVE);
      user.clearFailedLogins();
    }
    if(memberships.findByCompany_IdAndUser_Id(invite.getCompanyId(),user.getId()).isPresent()) return false;
    Company company=companies.findById(invite.getCompanyId()).filter(c->c.getStatus()==CompanyStatus.ACTIVE).orElse(null);
    if(company==null) return false;
    CompanyMembership membership=new CompanyMembership(company,user,invite.getMembershipRole());
    memberships.save(membership); invite.accept(now);
    audit(company,user,membership,"INVITATION_ACCEPTED",invite.getId());
    return true;
  }

  @Transactional
  public InvitationResponse invite(UUID companyId,UUID actorUserId,UUID actorMembershipId,String email,MembershipRole role) {
    if(role==MembershipRole.OWNER) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"invalid_role");
    String normalized=normalize(email), raw=generateSecret(); Instant now=Instant.now();
    CompanyInvitation invitation=new CompanyInvitation(companyId,actorMembershipId,normalized,role,hash(raw),now,now.plus(Duration.ofDays(7)));
    invitations.save(invitation);
    Company company=companies.findById(companyId).orElseThrow(); PlatformUser actor=users.findById(actorUserId).orElseThrow();
    audit(company,actor,memberships.findById(actorMembershipId).orElseThrow(),"MEMBER_INVITED",invitation.getId());
    accountEmail.invitation(normalized,raw);
    return new InvitationResponse(invitation.getId(),normalized,role.name(),invitation.getExpiresAt(),exposeDevTokens?raw:null);
  }

  @Transactional
  public void revokeInvitation(UUID companyId,UUID invitationId,UUID actorUserId,UUID actorMembershipId) {
    CompanyInvitation item=invitations.findByCompanyIdAndIdAndRevokedAtIsNullAndAcceptedAtIsNull(companyId,invitationId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"invitation_not_found"));
    item.revoke(Instant.now());
    audit(companies.findById(companyId).orElseThrow(),users.findById(actorUserId).orElseThrow(),memberships.findById(actorMembershipId).orElseThrow(),"INVITATION_REVOKED",invitationId);
  }

  @Transactional
  public String requestAccountToken(String email,AccountActionToken.Purpose purpose) {
    PlatformUser user=users.findByEmail(normalize(email)).orElse(null);
    if(user==null || (purpose==AccountActionToken.Purpose.VERIFY_EMAIL && user.isEmailVerified())) return null;
    actionTokens.deleteAllByUserIdAndPurpose(user.getId(),purpose);
    String token=createActionToken(user,purpose,purpose==AccountActionToken.Purpose.VERIFY_EMAIL?Duration.ofHours(24):Duration.ofMinutes(30));
    if(purpose==AccountActionToken.Purpose.VERIFY_EMAIL) accountEmail.verification(user.getEmail(),token);
    else accountEmail.passwordReset(user.getEmail(),token);
    return token;
  }

  @Transactional
  public boolean verifyEmail(String raw) {
    AccountActionToken token=validActionToken(raw,AccountActionToken.Purpose.VERIFY_EMAIL);
    if(token==null) return false;
    PlatformUser user=users.findById(token.getUserId()).orElse(null); if(user==null) return false;
    token.use(Instant.now()); user.setEmailVerified(true); if(user.getStatus()==UserStatus.INVITED) user.setStatus(UserStatus.ACTIVE);
    return true;
  }

  @Transactional
  public boolean resetPassword(String raw,String password,String confirmation) {
    if(!strongPassword(password) || !password.equals(confirmation)) return false;
    AccountActionToken token=validActionToken(raw,AccountActionToken.Purpose.RESET_PASSWORD); if(token==null) return false;
    PlatformUser user=users.findById(token.getUserId()).orElse(null); if(user==null) return false;
    token.use(Instant.now()); user.setPasswordHash(passwords.encode(password)); user.unlockAfterPasswordReset(); revokeAll(user.getId()); return true;
  }

  @Transactional
  public void logout(String raw,UUID userId,UUID currentSessionId) {
    if(raw!=null) sessions.findByTokenHash(hash(raw)).filter(s->s.getUserId().equals(userId)&&s.getRevokedAt()==null).ifPresent(s->s.revoke(Instant.now(),null));
    if(currentSessionId!=null) sessions.findById(currentSessionId).filter(s->s.getUserId().equals(userId)&&s.getRevokedAt()==null).ifPresent(s->s.revoke(Instant.now(),null));
  }
  @Transactional public void logoutAll(UUID userId) { revokeAll(userId); }

  @Transactional(readOnly=true)
  public MeResponse me(UUID userId,UUID companyId,UUID membershipId) {
    PlatformUser user=users.findById(userId).orElseThrow(AuthService::invalidCredentials);
    CompanyMembership membership=memberships.findByIdAndCompany_IdAndUser_IdAndStatus(membershipId,companyId,userId,MembershipStatus.ACTIVE).orElseThrow(AuthService::invalidCredentials);
    return new MeResponse(safeUser(user),safeCompany(membership));
  }

  @Transactional(readOnly=true)
  public CompanyList companiesFor(UUID userId) { return new CompanyList(memberships.findAllByUser_IdAndStatusAndCompany_Status(userId,MembershipStatus.ACTIVE,CompanyStatus.ACTIVE).stream().map(AuthService::safeCompany).toList()); }

  public Issued issue(PlatformUser user,CompanyMembership membership) {
    Instant now=Instant.now(), expiry=now.plus(accessTtl);
    String raw=generateSecret();
    RefreshSession session=sessions.save(new RefreshSession(user.getId(),membership.getCompany().getId(),membership.getId(),hash(raw),now,now.plus(refreshTtl)));
    JwtClaimsSet claims=JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString()).issuedAt(now).expiresAt(expiry).id(UUID.randomUUID().toString())
        .claim("company_id",membership.getCompany().getId().toString()).claim("membership_id",membership.getId().toString()).claim("session_id",session.getId().toString())
        .claim("role",membership.getMembershipRole().name()).build();
    String access=jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    return new Issued(new TokenResponse(access,"Bearer",accessTtl.toSeconds(),safeUser(user),safeCompany(membership)),raw,session);
  }

  private AccountActionToken validActionToken(String raw,AccountActionToken.Purpose purpose) {
    if(raw==null)return null;
    AccountActionToken token=actionTokens.findByTokenHashAndPurposeForUpdate(hash(raw),purpose).orElse(null);
    if(token==null || token.getUsedAt()!=null || !token.getExpiresAt().isAfter(Instant.now())) return null;
    return token;
  }
  private String createActionToken(PlatformUser user,AccountActionToken.Purpose purpose,Duration ttl) {
    String raw=generateSecret(); actionTokens.save(new AccountActionToken(user.getId(),hash(raw),purpose,Instant.now(),Instant.now().plus(ttl))); return raw;
  }
  private void revokeAll(UUID userId) { sessions.findAllByUserIdAndRevokedAtIsNull(userId).forEach(s->s.revoke(Instant.now(),null)); }
  private void audit(Company company,PlatformUser user,CompanyMembership membership,String type,UUID subject) {
    AuditEvent event=new AuditEvent(company,type,"AUTH",subject,"{}",Instant.now());
    if(user!=null && membership!=null) event.setActor(user.getId(),membership.getId());
    auditEvents.save(event);
  }
  private static SafeUser safeUser(PlatformUser u){return new SafeUser(u.getId(),u.getEmail(),u.getDisplayName());}
  private static SafeCompany safeCompany(CompanyMembership m){Company c=m.getCompany();return new SafeCompany(c.getId(),c.getName(),c.getLegalName(),c.getBusinessEmail(),m.getId(),m.getMembershipRole().name());}
  private static String normalize(String email){return email.trim().toLowerCase(Locale.ROOT);}
  private static boolean strongPassword(String value){return value!=null && value.length()>=12 && value.length()<=72 && value.getBytes(StandardCharsets.UTF_8).length<=72 && value.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$");}
  private static String trimToNull(String value){return value==null||value.isBlank()?null:value.trim();}
  private String slug(String value){String base=value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");if(base.isBlank())base="company";return (base+"-"+UUID.randomUUID().toString().substring(0,8)).substring(0,Math.min(100,base.length()+9));}
  private static String generateSecret(){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
  private static String hash(String raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
  private static ResponseStatusException invalidCredentials(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"invalid_credentials");}
  private static ResponseStatusException badRequest(String code){return new ResponseStatusException(HttpStatus.BAD_REQUEST,code);}
  private static ResponseStatusException conflict(String code){return new ResponseStatusException(HttpStatus.CONFLICT,code);}
  public boolean secureCookie(){return secureCookie;}
  public boolean exposeDevTokens(){return exposeDevTokens;}
  public record Issued(TokenResponse response,String refreshToken,RefreshSession session){}
}
