package com.mediacheck.media;

import com.mediacheck.domain.EvidenceStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class LicenceApplicabilityEngineTest {

  private static Map<String, Object> asset(UUID id, String title) {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("title", title);
    return map;
  }

  private static Map<String, Object> licence(UUID id, String ref, String status, Instant from, Instant until, String termsJson) {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("reference", ref);
    map.put("status", status);
    map.put("validFrom", from);
    map.put("validUntil", until);
    map.put("termsJson", termsJson);
    return map;
  }

  @Test
  void passesWhenNoRightsAssetsMatched() {
    var result = LicenceApplicabilityEngine.evaluate(List.of(), List.of(), "WEB", "US", "COMMERCIAL", Instant.now());
    assertThat(result.status()).isEqualTo(EvidenceStatus.PASS);
    assertThat(result.violations()).isEmpty();
  }

  @Test
  void advisoryWhenRightsAssetMatchedWithoutLicenceRecord() {
    var matchedAssets = List.of(asset(UUID.randomUUID(), "Sample Asset"));
    var result = LicenceApplicabilityEngine.evaluate(matchedAssets, List.of(), "WEB", "US", "COMMERCIAL", Instant.now());

    assertThat(result.status()).isEqualTo(EvidenceStatus.ADVISORY_FLAG);
    assertThat(result.violations()).anyMatch(v -> v.contains("No licence record found"));
  }

  @Test
  void passesWhenActiveLicenceCoversChannelTerritoryAndDates() {
    var matchedAssets = List.of(asset(UUID.randomUUID(), "Protected Photo"));
    var now = Instant.now();
    var licences = List.of(licence(
        UUID.randomUUID(),
        "LIC-2026-001",
        "ACTIVE",
        now.minusSeconds(86400),
        now.plusSeconds(86400 * 30),
        "{\"permittedChannels\":[\"WEB\",\"SOCIAL\"],\"permittedTerritories\":[\"US\",\"EU\"],\"permittedUse\":\"COMMERCIAL\"}"
    ));

    var result = LicenceApplicabilityEngine.evaluate(matchedAssets, licences, "WEB", "US", "COMMERCIAL", now);

    assertThat(result.status()).isEqualTo(EvidenceStatus.PASS);
    assertThat(result.violations()).isEmpty();
    assertThat(result.summary()).contains("Active licence coverage confirmed");
  }

  @Test
  void failsWhenLicenceIsExpired() {
    var matchedAssets = List.of(asset(UUID.randomUUID(), "Protected Photo"));
    var now = Instant.now();
    var licences = List.of(licence(
        UUID.randomUUID(),
        "LIC-EXPIRED",
        "EXPIRED",
        now.minusSeconds(86400 * 60),
        now.minusSeconds(86400 * 10),
        "{}"
    ));

    var result = LicenceApplicabilityEngine.evaluate(matchedAssets, licences, "WEB", "US", "COMMERCIAL", now);

    assertThat(result.status()).isEqualTo(EvidenceStatus.FAIL);
    assertThat(result.violations()).anyMatch(v -> v.contains("EXPIRED"));
  }

  @Test
  void failsWhenLicenceIsRevoked() {
    var matchedAssets = List.of(asset(UUID.randomUUID(), "Protected Photo"));
    var licences = List.of(licence(
        UUID.randomUUID(),
        "LIC-REVOKED",
        "REVOKED",
        Instant.now().minusSeconds(86400),
        Instant.now().plusSeconds(86400),
        "{}"
    ));

    var result = LicenceApplicabilityEngine.evaluate(matchedAssets, licences, "WEB", "US", "COMMERCIAL", Instant.now());

    assertThat(result.status()).isEqualTo(EvidenceStatus.FAIL);
    assertThat(result.violations()).anyMatch(v -> v.contains("REVOKED"));
  }

  @Test
  void failsWhenChannelIsNotPermittedByLicence() {
    var matchedAssets = List.of(asset(UUID.randomUUID(), "Protected Photo"));
    var licences = List.of(licence(
        UUID.randomUUID(),
        "LIC-CHANNELS",
        "ACTIVE",
        Instant.now().minusSeconds(86400),
        Instant.now().plusSeconds(86400),
        "{\"permittedChannels\":[\"PRINT\",\"INTERNAL\"]}"
    ));

    var result = LicenceApplicabilityEngine.evaluate(matchedAssets, licences, "BROADCAST", "US", "COMMERCIAL", Instant.now());

    assertThat(result.status()).isEqualTo(EvidenceStatus.FAIL);
    assertThat(result.violations()).anyMatch(v -> v.contains("BROADCAST") && v.contains("not permitted"));
  }

  @Test
  void failsWhenTerritoryIsNotPermittedByLicence() {
    var matchedAssets = List.of(asset(UUID.randomUUID(), "Protected Photo"));
    var licences = List.of(licence(
        UUID.randomUUID(),
        "LIC-TERRITORY",
        "ACTIVE",
        Instant.now().minusSeconds(86400),
        Instant.now().plusSeconds(86400),
        "{\"permittedTerritories\":[\"UK\",\"EU\"]}"
    ));

    var result = LicenceApplicabilityEngine.evaluate(matchedAssets, licences, "WEB", "JAPAN", "COMMERCIAL", Instant.now());

    assertThat(result.status()).isEqualTo(EvidenceStatus.FAIL);
    assertThat(result.violations()).anyMatch(v -> v.contains("JAPAN") && v.contains("not permitted"));
  }
}
