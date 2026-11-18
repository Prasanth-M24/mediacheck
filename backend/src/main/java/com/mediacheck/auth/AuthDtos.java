package com.mediacheck.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
  private AuthDtos() {}
  public record RegisterCompanyRequest(@NotBlank @Size(max=200) String companyName, @Size(max=200) String legalName,
      @NotBlank @Email @Size(max=320) String businessEmail, @Size(max=40) String contactNumber,
      @Size(max=512) String website, @NotBlank @Size(max=200) String administratorFullName,
      @NotBlank @Email @Size(max=320) String administratorEmail,
      @NotBlank @Size(min=12,max=72) @Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",message="Password must include upper and lower case letters, a number, and a symbol") String password,
      @NotBlank String passwordConfirmation) {}
  public record LoginRequest(@NotBlank @Email String email,@NotBlank String password,UUID companyId) {}
  public record TokenResponse(String accessToken,String tokenType,long expiresIn,SafeUser user,SafeCompany activeCompany) {}
  public record RegisterResponse(UUID userId,UUID companyId,String status,String message,String developmentVerificationToken) {}
  public record SafeUser(UUID id,String email,String displayName) {}
  public record SafeCompany(UUID id,String name,String legalName,String businessEmail,UUID membershipId,String role) {}
  public record MeResponse(SafeUser user,SafeCompany activeCompany) {}
  public record CompanyList(List<SafeCompany> companies) {}
  public record SwitchCompanyRequest(@jakarta.validation.constraints.NotNull UUID companyId) {}
  public record TokenRequest(@NotBlank String token) {}
  public record EmailRequest(@NotBlank @Email String email) {}
  public record AcceptInvitationRequest(@NotBlank String token,@Size(max=200) String fullName,@Size(max=72) @Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,72}$") String password,String passwordConfirmation) {}
  public record PasswordTokenRequest(@NotBlank String token,@NotBlank @Size(min=12,max=72) @Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$") String password,@NotBlank String passwordConfirmation) {}
  public record InvitationRequest(@NotBlank @Email @Size(max=320) String email,@jakarta.validation.constraints.NotNull com.mediacheck.domain.MembershipRole role) {}
  public record InvitationResponse(UUID id,String email,String role,Instant expiresAt,String developmentInvitationToken) {}
  public record CompanyUpdateRequest(@NotBlank @Size(max=200) String name,@Size(max=200) String legalName,
      @NotBlank @Email @Size(max=320) String businessEmail,@Size(max=40) String contactNumber,@Size(max=512) String website) {}
  public record MemberResponse(UUID membershipId,UUID userId,String email,String displayName,String role,String status) {}
  public record RoleRequest(@jakarta.validation.constraints.NotNull com.mediacheck.domain.MembershipRole role) {}
  public record StatusRequest(@jakarta.validation.constraints.NotNull com.mediacheck.domain.MembershipStatus status) {}
  public record Message(String message) {}
}
