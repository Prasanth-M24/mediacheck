package com.mediacheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mediacheck")
public record ServiceProperties(String version, String aiServiceBaseUrl, Storage storage) {
  public record Storage(String provider, String path, B2 b2) { }
  public record B2(boolean configured, String endpoint, String bucketName, String region) { }
}
