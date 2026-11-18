package com.mediacheck.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediacheck.domain.Company;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
  Optional<Company> findBySlug(String slug);
  boolean existsBySlug(String slug);
  boolean existsByBusinessEmailIgnoreCase(String email);
  boolean existsByBusinessEmailIgnoreCaseAndIdNot(String email, UUID id);
}
