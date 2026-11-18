package com.mediacheck.health;

import com.mediacheck.config.ServiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemInfoController.class)
@ActiveProfiles("test")
@Import(SystemInfoControllerTest.Config.class)
class SystemInfoControllerTest {
  @Autowired MockMvc mvc;
  @Test void returnsSafeServiceInfo() throws Exception { mvc.perform(get("/api/v1/system/info")).andExpect(status().isOk()).andExpect(jsonPath("$.service").value("mediacheck-backend")).andExpect(jsonPath("$.apiVersion").value("v1")); }
  @TestConfiguration static class Config {
    @Bean ServiceProperties serviceProperties() { return new ServiceProperties("test", "http://ai", new ServiceProperties.Storage("local", "./mediacheck-data", new ServiceProperties.B2(false, "", "", ""))); }
    @Bean SecurityFilterChain testSecurity(HttpSecurity http) throws Exception { return http.csrf(AbstractHttpConfigurer::disable).authorizeHttpRequests(a->a.anyRequest().permitAll()).build(); }
  }
}
