package com.mediacheck.domain;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class FlywaySchemaConfigurationTest {
  @Test
  void configUsesFlywayAndHibernateValidationAndIncludesCoreMigration() throws IOException {
    String config;
    try (InputStream stream = getClass().getResourceAsStream("/application.yml")) {
      assertThat(stream).isNotNull();
      config = new String(stream.readAllBytes());
    }
    assertThat(config).contains("ddl-auto: validate", "enabled: true", "locations: classpath:db/migration");

    String migration;
    try (InputStream stream = getClass().getResourceAsStream("/db/migration/V1__create_core_domain_schema.sql")) {
      assertThat(stream).isNotNull();
      migration = new String(stream.readAllBytes());
    }
    assertThat(migration).contains("CREATE TABLE companies", "CREATE TABLE audit_events", "REFERENCES media_assets(company_id, id)");
  }
}
