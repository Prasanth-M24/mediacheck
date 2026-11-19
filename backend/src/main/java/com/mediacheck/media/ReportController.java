package com.mediacheck.media;

import com.mediacheck.auth.AuthController;
import com.mediacheck.media.MediaReviewDtos.ReportSummary;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/reports")
public class ReportController {
  private final MediaReviewService service;
  public ReportController(MediaReviewService service){this.service=service;}
  @GetMapping @PreAuthorize("hasAnyRole('OWNER','ADMIN','REVIEWER','MEMBER')") public List<ReportSummary> list(@AuthenticationPrincipal Jwt jwt){return service.reports(AuthController.companyId(jwt));}
}
