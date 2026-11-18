package com.mediacheck.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.env.Environment;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.mediacheck.domain.MembershipStatus;
import com.mediacheck.domain.UserStatus;
import com.mediacheck.repository.CompanyMembershipRepository;
import com.mediacheck.repository.PlatformUserRepository;
import com.mediacheck.repository.RefreshSessionRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
  @Bean ApplicationRunner productionSecurityGuard(Environment environment,
      @Value("${mediacheck.security.dev-token-delivery:false}") boolean devDelivery,
      @Value("${mediacheck.security.refresh-cookie-secure:false}") boolean secureCookie,
      @Value("${mediacheck.email.enabled:false}") boolean emailEnabled,
      @Value("${spring.mail.host:}") String mailHost,
      @Value("${spring.mail.username:}") String mailUsername,
      @Value("${spring.mail.password:}") String mailPassword,
      @Value("${mediacheck.email.from:}") String mailFrom,
      @Value("${spring.mail.properties.mail.smtp.auth:true}") boolean smtpAuth,
      @Value("${spring.mail.properties.mail.smtp.starttls.required:true}") boolean smtpStartTls) {
    return args -> {
      boolean production=java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
      production = production || "production".equalsIgnoreCase(environment.getProperty("APP_ENV"));
      if(production && (devDelivery || environment.getProperty("MEDIACHECK_DEV_TOKEN_DELIVERY", Boolean.class, false))) throw new IllegalStateException("Development token delivery must be disabled in production");
      if(production && !secureCookie) throw new IllegalStateException("Secure refresh cookies are required in production");
      if(production && !"https".equalsIgnoreCase(java.net.URI.create(environment.getProperty("mediacheck.security.issuer", "http://localhost:8080")).getScheme())) throw new IllegalStateException("A secure HTTPS JWT issuer is required in production");
      if(production && (!emailEnabled || mailHost.isBlank() || mailUsername.isBlank() || mailPassword.isBlank() || mailFrom.isBlank() || !smtpAuth || !smtpStartTls)) throw new IllegalStateException("Configure authenticated STARTTLS SMTP before enabling production accounts");
    };
  }
  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

  @Bean
  @Profile("test")
  SecretKey testSigningKey() { return new SecretKeySpec("test-only-key-must-never-be-used-outside-tests-32bytes".getBytes(StandardCharsets.UTF_8), "HmacSHA256"); }

  @Bean
  @Profile("!test")
  SecretKey signingKey(@Value("${mediacheck.security.jwt-secret:}") String configured) {
    if (configured == null || configured.isBlank()) throw new IllegalStateException("MEDIACHECK_JWT_SECRET must be configured");
    byte[] bytes;
    try { bytes = Base64.getDecoder().decode(configured); }
    catch (IllegalArgumentException ex) { throw new IllegalStateException("MEDIACHECK_JWT_SECRET must be Base64 encoded", ex); }
    if (bytes.length < 32) throw new IllegalStateException("MEDIACHECK_JWT_SECRET must contain at least 256 bits");
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

  @Bean JwtDecoder jwtDecoder(SecretKey key, @Value("${mediacheck.security.issuer:http://localhost:8080}") String issuer) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    return decoder;
  }

  @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, CompanyMembershipRepository memberships, PlatformUserRepository users, RefreshSessionRepository sessions,
      @Value("${mediacheck.security.cors-origins:http://localhost:5173,http://127.0.0.1:5173}") String origins) throws Exception {
    var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfRepository.setCookiePath("/");
    http.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository));
    http.cors(cors -> cors.configurationSource(request -> {
      var c = new org.springframework.web.cors.CorsConfiguration();
      c.setAllowedOrigins(java.util.Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
      c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
      c.setAllowedHeaders(List.of("Authorization","Content-Type","X-XSRF-TOKEN"));
      c.setAllowCredentials(true);
      c.setMaxAge(3600L);
      return c;
    }));
    http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.OPTIONS,"/**").permitAll()
        .requestMatchers("/actuator/health","/api/v1/system/info","/api/v1/auth/register-company","/api/v1/auth/login",
            "/api/v1/auth/refresh","/api/v1/auth/accept-invitation","/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password","/api/v1/auth/verify-email","/api/v1/auth/resend-verification","/api/v1/auth/csrf").permitAll()
        .anyRequest().authenticated());
    http.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(token ->
        liveMembershipAuthentication(token, memberships, users, sessions)))
        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
        .accessDeniedHandler(new BearerTokenAccessDeniedHandler()));
    http.exceptionHandling(errors -> errors.authenticationEntryPoint((request,response,exception)->{
      response.setStatus(401); response.setContentType("application/json"); response.getWriter().write("{\"error\":\"unauthorized\"}");
    }).accessDeniedHandler((request,response,exception)->{
      response.setStatus(403); response.setContentType("application/json"); response.getWriter().write("{\"error\":\"forbidden\"}");
    }));
    http.httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable);
    return http.build();
  }

  private AbstractAuthenticationToken liveMembershipAuthentication(Jwt token, CompanyMembershipRepository memberships, PlatformUserRepository users, RefreshSessionRepository sessions) {
    try {
      var userId = java.util.UUID.fromString(token.getSubject());
      var companyId = java.util.UUID.fromString(token.getClaimAsString("company_id"));
      var membershipId = java.util.UUID.fromString(token.getClaimAsString("membership_id"));
      var sessionId = java.util.UUID.fromString(token.getClaimAsString("session_id"));
      var user = users.findById(userId).filter(u -> u.getStatus() == UserStatus.ACTIVE && u.isEmailVerified()).orElseThrow();
      var membership = memberships.findByIdAndCompany_IdAndUser_IdAndStatusAndCompany_Status(membershipId, companyId, userId, MembershipStatus.ACTIVE, com.mediacheck.domain.CompanyStatus.ACTIVE).orElseThrow();
      var session = sessions.findById(sessionId).filter(s -> s.getUserId().equals(userId) && s.getCompanyId().equals(companyId)
          && s.getMembershipId().equals(membershipId) && s.getRevokedAt()==null && s.getExpiresAt().isAfter(java.time.Instant.now())).orElseThrow();
      if (!membership.getMembershipRole().name().equals(token.getClaimAsString("role"))) throw new BadCredentialsException("Membership role changed");
      var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + membership.getMembershipRole().name()));
      return new JwtAuthenticationToken(token, authorities, userId.toString());
    } catch (Exception ex) { throw new BadCredentialsException("Invalid authentication context"); }
  }
}
