package com.mediacheck.auth;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.mediacheck.auth.AuthDtos.*;
import com.mediacheck.domain.MembershipStatus;
import com.mediacheck.auth.AuthController;
import com.mediacheck.auth.AuthService;
import com.mediacheck.domain.MembershipRole;

@RestController
@RequestMapping("/api/v1/company")
public class CompanyController {
  private final CompanyAdministrationService admin;
  private final AuthService auth;
  public CompanyController(CompanyAdministrationService admin,AuthService auth){this.admin=admin;this.auth=auth;}

  @GetMapping("/profile") public CompanyUpdateRequest profile(@AuthenticationPrincipal Jwt jwt){return admin.getProfile(AuthController.companyId(jwt));}
  @PutMapping("/profile") @PreAuthorize("hasRole('OWNER')") public CompanyUpdateRequest updateProfile(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CompanyUpdateRequest request){
    return admin.updateProfile(AuthController.companyId(jwt),AuthController.userId(jwt),AuthController.membershipId(jwt),request);
  }
  @GetMapping("/members") @PreAuthorize("hasAnyRole('OWNER','ADMIN','REVIEWER')") public List<MemberResponse> members(@AuthenticationPrincipal Jwt jwt){return admin.members(AuthController.companyId(jwt));}
  @GetMapping("/invitations") @PreAuthorize("hasAnyRole('OWNER','ADMIN')") public List<InvitationResponse> invitations(@AuthenticationPrincipal Jwt jwt){return admin.invitations(AuthController.companyId(jwt));}
  @PostMapping("/invitations") @PreAuthorize("hasAnyRole('OWNER','ADMIN')") public InvitationResponse invite(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody InvitationRequest request){
    if(request.role()==MembershipRole.ADMIN && !"OWNER".equals(currentRole())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"owner_required_for_admin_role");
    return auth.invite(AuthController.companyId(jwt),AuthController.userId(jwt),AuthController.membershipId(jwt),request.email(),request.role());
  }
  @DeleteMapping("/invitations/{id}") @PreAuthorize("hasAnyRole('OWNER','ADMIN')") public org.springframework.http.ResponseEntity<Void> revokeInvite(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
    admin.revokeInvitation(AuthController.companyId(jwt),id,AuthController.userId(jwt),AuthController.membershipId(jwt));return org.springframework.http.ResponseEntity.noContent().build();
  }
  @PatchMapping("/members/{membershipId}/role") @PreAuthorize("hasAnyRole('OWNER','ADMIN')") public MemberResponse role(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID membershipId,@Valid @RequestBody RoleRequest request){
    return admin.changeRole(AuthController.companyId(jwt),membershipId,AuthController.userId(jwt),AuthController.membershipId(jwt),currentRole(),request.role());
  }
  @PatchMapping("/members/{membershipId}/status") @PreAuthorize("hasAnyRole('OWNER','ADMIN')") public MemberResponse status(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID membershipId,@Valid @RequestBody StatusRequest request){
    return admin.changeStatus(AuthController.companyId(jwt),membershipId,AuthController.userId(jwt),AuthController.membershipId(jwt),currentRole(),request.status());
  }
  private String currentRole(){return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().map(a->a.getAuthority()).filter(a->a.startsWith("ROLE_")).map(a->a.substring(5)).findFirst().orElse("MEMBER");}
}
