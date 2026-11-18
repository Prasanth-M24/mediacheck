package com.mediacheck.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.mediacheck.auth.AuthDtos.*;
import com.mediacheck.domain.*;
import com.mediacheck.repository.*;

@Service
public class CompanyAdministrationService {
  private final CompanyRepository companies;
  private final CompanyMembershipRepository memberships;
  private final PlatformUserRepository users;
  private final CompanyInvitationRepository invitations;
  private final AuthService auth;
  private final AuditEventRepository audit;
  public CompanyAdministrationService(CompanyRepository companies,CompanyMembershipRepository memberships,PlatformUserRepository users,
      CompanyInvitationRepository invitations,AuthService auth,AuditEventRepository audit){this.companies=companies;this.memberships=memberships;this.users=users;this.invitations=invitations;this.auth=auth;this.audit=audit;}

  @Transactional(readOnly=true)
  public CompanyUpdateRequest getProfile(UUID companyId){Company c=company(companyId);return new CompanyUpdateRequest(c.getName(),c.getLegalName(),c.getBusinessEmail(),c.getContactNumber(),c.getWebsite());}

  @Transactional
  public CompanyUpdateRequest updateProfile(UUID companyId,UUID actorId,UUID actorMembershipId,CompanyUpdateRequest request){
    Company c=company(companyId);String email=request.businessEmail().trim().toLowerCase(java.util.Locale.ROOT);
    if(companies.existsByBusinessEmailIgnoreCaseAndIdNot(email,companyId))throw new ResponseStatusException(HttpStatus.CONFLICT,"company_profile_unavailable");
    c.updateName(request.name().trim());c.setRegistrationDetails(blankNull(request.legalName()),email,blankNull(request.contactNumber()),blankNull(request.website()));
    addAudit(c,actorId,actorMembershipId,"COMPANY_PROFILE_UPDATED",companyId);return request;
  }

  @Transactional(readOnly=true)
  public List<MemberResponse> members(UUID companyId){return memberships.findAllByCompany_IdOrderByUser_DisplayNameAsc(companyId).stream()
      .map(m->new MemberResponse(m.getId(),m.getUser().getId(),m.getUser().getEmail(),m.getUser().getDisplayName(),m.getMembershipRole().name(),m.getStatus().name())).toList();}

  @Transactional(readOnly=true)
  public List<InvitationResponse> invitations(UUID companyId){return invitations.findAllByCompanyIdAndAcceptedAtIsNullAndRevokedAtIsNull(companyId).stream()
      .map(i->new InvitationResponse(i.getId(),i.getEmail(),i.getMembershipRole().name(),i.getExpiresAt(),null)).toList();}

  @Transactional
  public MemberResponse changeRole(UUID companyId,UUID membershipId,UUID actorId,UUID actorMembershipId,String actorRole,MembershipRole role){
    CompanyMembership target=membership(companyId,membershipId);
    if(role==MembershipRole.OWNER && !actorRole.equals("OWNER"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"owner_required");
    if(target.getMembershipRole()==MembershipRole.OWNER){
      if(!actorRole.equals("OWNER"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"owner_required");
      long otherOwners=memberships.findAllByCompany_IdAndMembershipRoleAndStatus(companyId,MembershipRole.OWNER,MembershipStatus.ACTIVE).stream().filter(m->!m.getId().equals(target.getId())).count();
      if(role!=MembershipRole.OWNER && otherOwners==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"company_requires_active_owner");
    }
    if(role==MembershipRole.ADMIN && !actorRole.equals("OWNER"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"owner_required_for_admin_role");
    target.setMembershipRole(role);addAudit(target.getCompany(),actorId,actorMembershipId,"MEMBERSHIP_ROLE_CHANGED",target.getId());return member(target);
  }

  @Transactional
  public MemberResponse changeStatus(UUID companyId,UUID membershipId,UUID actorId,UUID actorMembershipId,String actorRole,MembershipStatus status){
    if(status==MembershipStatus.INVITED)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"invalid_membership_status");
    CompanyMembership target=membership(companyId,membershipId);
    if(target.getMembershipRole()==MembershipRole.OWNER){
      if(!actorRole.equals("OWNER"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"owner_required");
      long otherOwners=memberships.findAllByCompany_IdAndMembershipRoleAndStatus(companyId,MembershipRole.OWNER,MembershipStatus.ACTIVE).stream().filter(m->!m.getId().equals(target.getId())).count();
      if(status==MembershipStatus.REVOKED && otherOwners==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"company_requires_active_owner");
    }
    target.setStatus(status);addAudit(target.getCompany(),actorId,actorMembershipId,"MEMBERSHIP_STATUS_CHANGED",target.getId());return member(target);
  }

  @Transactional
  public void revokeInvitation(UUID companyId,UUID id,UUID actorId,UUID actorMembershipId){auth.revokeInvitation(companyId,id,actorId,actorMembershipId);}

  private Company company(UUID id){return companies.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"company_not_found"));}
  private CompanyMembership membership(UUID company,UUID id){return memberships.findById(id).filter(m->m.getCompany().getId().equals(company)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"member_not_found"));}
  private void addAudit(Company c,UUID actorId,UUID membershipId,String type,UUID subject){AuditEvent e=new AuditEvent(c,type,"COMPANY",subject,"{}",Instant.now());e.setActor(actorId,membershipId);audit.save(e);}
  private static MemberResponse member(CompanyMembership m){return new MemberResponse(m.getId(),m.getUser().getId(),m.getUser().getEmail(),m.getUser().getDisplayName(),m.getMembershipRole().name(),m.getStatus().name());}
  private static String blankNull(String s){return s==null||s.isBlank()?null:s.trim();}
}
