package com.mediacheck.health;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mediacheck.config.ServiceProperties;

@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {
  private final ServiceProperties properties;

  public SystemInfoController(ServiceProperties properties) {
    this.properties = properties;
  }

  @GetMapping("/info")
  public Map<String, String> info() {
    return Map.of("service", "mediacheck-backend", "version", properties.version(), "apiVersion", "v1");
  }
}
