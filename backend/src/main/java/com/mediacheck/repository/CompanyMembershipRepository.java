package com.mediacheck.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediacheck.domain.CompanyMembership;
import com.mediacheck.domain.MembershipStatus;

public interface CompanyMembershipRepository extends JpaRepository<CompanyMembership, UUID> {
  List<CompanyMembership> findAllByCompany_Id(UUID companyId);

  Optional<CompanyMembership> findByCompany_IdAndUser_Id(UUID companyId, UUID userId);
  Optional<CompanyMembership> findByIdAndCompany_IdAndUser_IdAndStatus(UUID id, UUID companyId, UUID userId, MembershipStatus status);
  Optional<CompanyMembership> findByIdAndCompany_IdAndUser_IdAndStatusAndCompany_Status(UUID id, UUID companyId, UUID userId, MembershipStatus status, com.mediacheck.domain.CompanyStatus companyStatus);
  List<CompanyMembership> findAllByUser_IdAndStatus(UUID userId, MembershipStatus status);
  List<CompanyMembership> findAllByUser_IdAndStatusAndCompany_Status(UUID userId, MembershipStatus status, com.mediacheck.domain.CompanyStatus companyStatus);
  Optional<CompanyMembership> findByCompany_IdAndUser_IdAndStatusAndCompany_Status(UUID companyId, UUID userId, MembershipStatus status, com.mediacheck.domain.CompanyStatus companyStatus);
  List<CompanyMembership> findAllByCompany_IdOrderByUser_DisplayNameAsc(UUID companyId);
  List<CompanyMembership> findAllByCompany_IdAndMembershipRoleAndStatus(UUID companyId, com.mediacheck.domain.MembershipRole role, MembershipStatus status);
}
