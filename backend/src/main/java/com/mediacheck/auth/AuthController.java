package com.mediacheck.auth;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import com.mediacheck.auth.AuthDtos.*;
import com.mediacheck.domain.AccountActionToken;
import com.mediacheck.domain.MembershipRole;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService auth;
  public AuthController(AuthService auth){this.auth=auth;}

  @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken());}
  @PostMapping("/register-company") public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterCompanyRequest request){return ResponseEntity.accepted().body(auth.registerCompany(request));}
  @PostMapping("/login") public TokenResponse login(@Valid @RequestBody LoginRequest request,HttpServletResponse response){return setCookie(auth.login(request),response);}
  @PostMapping("/refresh") public ResponseEntity<?> refresh(@CookieValue(name="MEDIACHECK_REFRESH",required=false) String token,HttpServletResponse response){
    AuthService.Issued issued=auth.refresh(token);if(issued==null){clearCookie(response);return ResponseEntity.status(401).body(Map.of("error","invalid_session"));}
    return ResponseEntity.ok(setCookie(issued,response));
  }
  @PostMapping("/accept-invitation") public ResponseEntity<?> accept(@Valid @RequestBody AcceptInvitationRequest request){
    boolean accepted=auth.acceptInvitation(request.token(),request.fullName(),request.password(),request.passwordConfirmation());
    return accepted?ResponseEntity.ok(new Message("Invitation accepted. Sign in to continue.")):ResponseEntity.badRequest().body(Map.of("error","invalid_or_expired_invitation"));
  }
  @PostMapping("/forgot-password") public Map<String,String> forgot(@Valid @RequestBody EmailRequest request){
    String token=auth.requestAccountToken(request.email(),AccountActionToken.Purpose.RESET_PASSWORD);
    return auth.exposeDevTokens()&&token!=null?Map.of("message","If the account exists, reset instructions were created.","developmentResetToken",token):Map.of("message","If the account exists, reset instructions were created.");
  }
  @PostMapping("/resend-verification") public Map<String,String> resendVerification(@Valid @RequestBody EmailRequest request){
    String token=auth.requestAccountToken(request.email(),AccountActionToken.Purpose.VERIFY_EMAIL);
    return auth.exposeDevTokens()&&token!=null?Map.of("message","If the account requires verification, instructions were created.","developmentVerificationToken",token):Map.of("message","If the account requires verification, instructions were created.");
  }
  @PostMapping("/reset-password") public ResponseEntity<?> reset(@Valid @RequestBody PasswordTokenRequest request){
    boolean ok=auth.resetPassword(request.token(),request.password(),request.passwordConfirmation());
    return ok?ResponseEntity.ok(new Message("Password updated.")):ResponseEntity.badRequest().body(Map.of("error","invalid_or_expired_token"));
  }
  @PostMapping("/verify-email") public ResponseEntity<?> verify(@Valid @RequestBody TokenRequest request){
    return auth.verifyEmail(request.token())?ResponseEntity.ok(new Message("Email verified.")):ResponseEntity.badRequest().body(Map.of("error","invalid_or_expired_token"));
  }
  @GetMapping("/me") public MeResponse me(@AuthenticationPrincipal Jwt jwt){return auth.me(userId(jwt),companyId(jwt),membershipId(jwt));}
  @GetMapping("/companies") public CompanyList companies(@AuthenticationPrincipal Jwt jwt){return auth.companiesFor(userId(jwt));}
  @PostMapping("/switch-company") public TokenResponse switchCompany(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody SwitchCompanyRequest request,
      @CookieValue(name="MEDIACHECK_REFRESH",required=false) String refresh,HttpServletResponse response){return setCookie(auth.switchCompany(userId(jwt),request.companyId(),refresh,sessionId(jwt)),response);}
  @PostMapping("/logout") public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt,@CookieValue(name="MEDIACHECK_REFRESH",required=false) String refresh,HttpServletResponse response){auth.logout(refresh,userId(jwt),sessionId(jwt));clearCookie(response);return ResponseEntity.noContent().build();}
  @PostMapping("/logout-all") public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal Jwt jwt,HttpServletResponse response){auth.logoutAll(userId(jwt));clearCookie(response);return ResponseEntity.noContent().build();}

  private TokenResponse setCookie(AuthService.Issued issued,HttpServletResponse response){
    ResponseCookie cookie=ResponseCookie.from("MEDIACHECK_REFRESH",issued.refreshToken()).httpOnly(true).secure(auth.secureCookie()).sameSite("Strict").path("/api/v1/auth").maxAge(Duration.ofDays(14)).build();
    response.addHeader(HttpHeaders.SET_COOKIE,cookie.toString());return issued.response();
  }
  private void clearCookie(HttpServletResponse response){response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("MEDIACHECK_REFRESH","").httpOnly(true).secure(auth.secureCookie()).sameSite("Strict").path("/api/v1/auth").maxAge(Duration.ZERO).build().toString());}
  public static UUID userId(Jwt jwt){return UUID.fromString(jwt.getSubject());} public static UUID companyId(Jwt jwt){return UUID.fromString(jwt.getClaimAsString("company_id"));} public static UUID membershipId(Jwt jwt){return UUID.fromString(jwt.getClaimAsString("membership_id"));} static UUID sessionId(Jwt jwt){return UUID.fromString(jwt.getClaimAsString("session_id"));}
}
