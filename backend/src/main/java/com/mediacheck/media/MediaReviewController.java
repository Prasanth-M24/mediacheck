package com.mediacheck.media;

import com.mediacheck.auth.AuthController;
import com.mediacheck.media.MediaReviewDtos.*;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/v1/media")
public class MediaReviewController {
  private final MediaReviewService service;
  public MediaReviewController(MediaReviewService service){this.service=service;}
  @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAnyRole('OWNER','ADMIN','REVIEWER','MEMBER')")
  public MediaDetail upload(@AuthenticationPrincipal Jwt jwt,@RequestPart("file") MultipartFile file,@RequestParam(required=false) String intendedChannel,@RequestParam(required=false) String intendedTerritory,@RequestParam(required=false) String intendedUse,@RequestParam(required=false) java.time.Instant plannedPublicationAt){return service.upload(AuthController.companyId(jwt),AuthController.userId(jwt),AuthController.membershipId(jwt),file,intendedChannel,intendedTerritory,intendedUse,plannedPublicationAt);}
  @GetMapping @PreAuthorize("hasAnyRole('OWNER','ADMIN','REVIEWER','MEMBER')") public PagedMedia list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(AuthController.companyId(jwt),page,size);}
  @GetMapping("/{id}") @PreAuthorize("hasAnyRole('OWNER','ADMIN','REVIEWER','MEMBER')") public MediaDetail detail(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return service.detail(AuthController.companyId(jwt),id);}
  @GetMapping("/{id}/file") @PreAuthorize("hasAnyRole('OWNER','ADMIN','REVIEWER','MEMBER')") public ResponseEntity<InputStreamResource> file(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){InputStream in=service.file(AuthController.companyId(jwt),id);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"inline").body(new InputStreamResource(in));}
  @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('OWNER','ADMIN')") public ResponseEntity<Void> archive(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){service.archive(AuthController.companyId(jwt),AuthController.userId(jwt),AuthController.membershipId(jwt),id);return ResponseEntity.noContent().build();}
  @GetMapping("/{id}/report") @PreAuthorize("hasAnyRole('OWNER','ADMIN','REVIEWER','MEMBER')") public ReviewReport report(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return service.report(AuthController.companyId(jwt),id);}
}
