package com.mediacheck.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthRateLimitFilterTest {
  @Test
  void enforcesThresholdOf30RequestsPerMinutePerIpAndPath() throws ServletException, IOException {
    AuthRateLimitFilter filter = new AuthRateLimitFilter();
    FilterChain chain = mock(FilterChain.class);

    // 30 requests should succeed
    for (int i = 1; i <= 30; i++) {
      MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
      req.setRemoteAddr("192.168.1.100");
      MockHttpServletResponse resp = new MockHttpServletResponse();
      filter.doFilter(req, resp, chain);
      assertThat(resp.getStatus()).isEqualTo(200);
    }
    verify(chain, times(30)).doFilter(any(), any());

    // 31st request from same IP and path must return 429
    MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    blockedReq.setRemoteAddr("192.168.1.100");
    MockHttpServletResponse blockedResp = new MockHttpServletResponse();
    filter.doFilter(blockedReq, blockedResp, chain);

    assertThat(blockedResp.getStatus()).isEqualTo(429);
    assertThat(blockedResp.getHeader("Retry-After")).isEqualTo("60");
    assertThat(blockedResp.getContentAsString()).contains("rate_limited");
    // chain.doFilter should still have been called only 30 times
    verify(chain, times(30)).doFilter(any(), any());

    // Request from a different IP is not blocked
    MockHttpServletRequest otherIpReq = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    otherIpReq.setRemoteAddr("192.168.1.101");
    MockHttpServletResponse otherIpResp = new MockHttpServletResponse();
    filter.doFilter(otherIpReq, otherIpResp, chain);
    assertThat(otherIpResp.getStatus()).isEqualTo(200);
    verify(chain, times(31)).doFilter(any(), any());

    // Request to non-auth path is not filtered
    MockHttpServletRequest nonAuthReq = new MockHttpServletRequest("GET", "/api/v1/company/profile");
    nonAuthReq.setRemoteAddr("192.168.1.100");
    MockHttpServletResponse nonAuthResp = new MockHttpServletResponse();
    filter.doFilter(nonAuthReq, nonAuthResp, chain);
    assertThat(nonAuthResp.getStatus()).isEqualTo(200);
    verify(chain, times(32)).doFilter(any(), any());
  }
}
