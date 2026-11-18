package com.mediacheck.registry;

import com.mediacheck.auth.AuthController;
import com.mediacheck.registry.RegistryDtos.*;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class RightsRegistryController {
  private final RightsRegistryService service;
  public RightsRegistryController(RightsRegistryService service) { this.service = service; }

  @GetMapping("/rights-assets")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','REVIEWER','MEMBER')")
  public PagedResponse<AssetResponse> assets(@AuthenticationPrincipal Jwt jwt, @RequestParam(required=false) String status, @RequestParam(required=false) String assetType, @RequestParam(required=false) String search, Pageable pageable) { return service.listAssets(AuthController.companyId(jwt), status, assetType, search, pageable); }
  @PostMapping("/rights-assets") @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public AssetResponse createAsset(@AuthenticationPrincipal Jwt jwt, @RequestBody @jakarta.validation.Valid CreateAssetRequest request) { return service.createAsset(AuthController.companyId(jwt), AuthController.userId(jwt), AuthController.membershipId(jwt), request); }
  @GetMapping("/rights-assets/{id}") @PreAuthorize("hasAnyRole('OWNER','ADMIN','REVIEWER','MEMBER')")
  public AssetResponse asset(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return service.getAsset(AuthController.companyId(jwt), id); }
  @PutMapping("/rights-assets/{id}") @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public AssetResponse updateAsset(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody @jakarta.validation.Valid UpdateAssetRequest request) { return service.updateAsset(AuthController.companyId(jwt), AuthController.userId(jwt), AuthController.membershipId(jwt), id, request); }
  @DeleteMapping("/rights-assets/{id}") @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public ResponseEntity<Void> archiveAsset(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { service.archiveAsset(AuthController.companyId(jwt), AuthController.userId(jwt), AuthController.membershipId(jwt), id); return ResponseEntity.noContent().build(); }
  @PostMapping(value="/rights-assets/{id}/file", consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public AssetResponse uploadAsset(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestPart("file") MultipartFile file) { return service.uploadAsset(AuthController.companyId(jwt), AuthController.userId(jwt), AuthController.membershipId(jwt), id, file); }
  @GetMapping("/rights-assets/{id}/file") @PreAuthorize("hasAnyRole('OWNER','ADMIN','REVIEWER','MEMBER')")
  public ResponseEntity<InputStreamResource> assetFile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { InputStream in = service.assetFile(AuthController.companyId(jwt), id); return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment").body(new InputStreamResource(in)); }

  @GetMapping("/licences") @PreAuthorize("hasAnyRole('OWNER','ADMIN','REVIEWER','MEMBER')")
  public PagedResponse<LicenceResponse> licences(@AuthenticationPrincipal Jwt jwt, @RequestParam(required=false) String status, @RequestParam(required=false) String search, Pageable pageable) { return service.listLicences(AuthController.companyId(jwt), status, search, pageable); }
  @PostMapping("/licences") @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public LicenceResponse createLicence(@AuthenticationPrincipal Jwt jwt, @RequestBody @jakarta.validation.Valid CreateLicenceRequest request) { return service.createLicence(AuthController.companyId(jwt), AuthController.userId(jwt), AuthController.membershipId(jwt), request); }
  @GetMapping("/licences/{id}") @PreAuthorize("hasAnyRole('OWNER','ADMIN','REVIEWER','MEMBER')")
  public LicenceResponse licence(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return service.getLicence(AuthController.companyId(jwt), id); }
  @PutMapping("/licences/{id}") @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public LicenceResponse updateLicence(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody @jakarta.validation.Valid UpdateLicenceRequest request) { return service.updateLicence(AuthController.companyId(jwt), AuthController.userId(jwt), AuthController.membershipId(jwt), id, request); }
  @PatchMapping("/licences/{id}/status") @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public LicenceResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody java.util.Map<String,String> body) { return service.changeLicenceStatus(AuthController.companyId(jwt), AuthController.userId(jwt), AuthController.membershipId(jwt), id, body.get("status")); }
  @GetMapping("/licences/{id}/documents") @PreAuthorize("hasAnyRole('OWNER','ADMIN','REVIEWER','MEMBER')")
  public List<DocumentResponse> documents(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return service.listDocuments(AuthController.companyId(jwt), id); }
  @PostMapping(value="/licences/{id}/documents", consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public DocumentResponse uploadDocument(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestPart("file") MultipartFile file) { return service.uploadDocument(AuthController.companyId(jwt), AuthController.userId(jwt), AuthController.membershipId(jwt), id, file); }
  @GetMapping("/licences/{licenceId}/documents/{documentId}/file") @PreAuthorize("hasAnyRole('OWNER','ADMIN','REVIEWER','MEMBER')")
  public ResponseEntity<InputStreamResource> documentFile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID licenceId, @PathVariable UUID documentId) { return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment").body(new InputStreamResource(service.documentFile(AuthController.companyId(jwt), licenceId, documentId))); }
  @DeleteMapping("/licences/{licenceId}/documents/{documentId}") @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public ResponseEntity<Void> deleteDocument(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID licenceId, @PathVariable UUID documentId) { service.deleteDocument(AuthController.companyId(jwt), AuthController.userId(jwt), AuthController.membershipId(jwt), licenceId, documentId); return ResponseEntity.noContent().build(); }

  @GetMapping("/rights-registry/dashboard") @PreAuthorize("hasAnyRole('OWNER','ADMIN','REVIEWER','MEMBER')")
  public DashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) { return service.dashboard(AuthController.companyId(jwt)); }
}
