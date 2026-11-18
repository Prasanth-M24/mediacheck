package com.mediacheck.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "MEDIACHECK_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class PostgresV4ValidationTest {
  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("MEDIACHECK_TEST_DATABASE_URL"));
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.datasource.username", () -> System.getenv("MEDIACHECK_TEST_DATABASE_USERNAME"));
    registry.add("spring.datasource.password", () -> System.getenv("MEDIACHECK_TEST_DATABASE_PASSWORD"));
    registry.add("spring.flyway.target", () -> "4");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }

  @Autowired JdbcTemplate jdbc;

  @Test
  void v4SchemaAndTenantMembershipForeignKeysArePresent() {
    assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version='4' and success", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema='public' and ((table_name='rights_assets' and column_name in ('file_size','sha256_checksum','created_by_membership_id')) or (table_name='licence_records' and column_name in ('name','licence_type','created_by_membership_id')) or (table_name='licence_documents' and column_name in ('file_size','uploaded_by_membership_id')))", Integer.class)).isEqualTo(8);
    assertThat(jdbc.queryForObject("select count(*) from information_schema.table_constraints where constraint_schema='public' and constraint_name in ('fk_rights_assets_created_by_membership','fk_licence_records_created_by_membership','fk_licence_documents_uploaded_by_membership')", Integer.class)).isEqualTo(3);
  }
}
