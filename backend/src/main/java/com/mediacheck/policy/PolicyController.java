package com.mediacheck.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediacheck.auth.AuthController;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/policies")
public class PolicyController {
  private final JdbcTemplate jdbc; private final ObjectMapper mapper;
  public PolicyController(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
  @GetMapping @PreAuthorize("hasAnyRole('OWNER','ADMIN','REVIEWER','MEMBER')") public List<Map<String,Object>> list(@AuthenticationPrincipal Jwt jwt){return jdbc.queryForList("SELECT id,name,status,created_at,updated_at FROM company_policies WHERE company_id=? ORDER BY created_at DESC",AuthController.companyId(jwt));}
  @PostMapping @PreAuthorize("hasAnyRole('OWNER','ADMIN')") public Map<String,Object> create(@AuthenticationPrincipal Jwt jwt,@RequestBody PolicyRequest request){if(request.name()==null||request.name().isBlank())throw bad("policy_name_required");validateJson(request.rulesJson());UUID c=AuthController.companyId(jwt),p=UUID.randomUUID(),v=UUID.randomUUID();jdbc.update("INSERT INTO company_policies(id,company_id,name,status) VALUES(?,?,?,'ACTIVE')",p,c,request.name().trim());jdbc.update("INSERT INTO policy_versions(id,company_id,policy_id,version_number,status,rules_json,published_at) VALUES(?,?,?,1,'ACTIVE',?::jsonb,CURRENT_TIMESTAMP)",v,c,p,request.rulesJson());return Map.of("id",p,"versionId",v,"name",request.name().trim(),"status","ACTIVE");}
  @PostMapping("/{id}/versions") @PreAuthorize("hasAnyRole('OWNER','ADMIN')") public Map<String,Object> version(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestBody PolicyRequest request){UUID c=AuthController.companyId(jwt);Integer n=jdbc.query("SELECT COALESCE(MAX(version_number),0)+1 FROM policy_versions WHERE company_id=? AND policy_id=?",rs->{rs.next();return rs.getInt(1);},c,id);if(jdbc.queryForObject("SELECT COUNT(*) FROM company_policies WHERE company_id=? AND id=?",Long.class,c,id)==0)throw notFound();validateJson(request.rulesJson());UUID v=UUID.randomUUID();jdbc.update("INSERT INTO policy_versions(id,company_id,policy_id,version_number,status,rules_json,published_at) VALUES(?,?,?,?,'ACTIVE',?::jsonb,CURRENT_TIMESTAMP)",v,c,id,n,request.rulesJson());return Map.of("id",v,"policyId",id,"versionNumber",n,"status","ACTIVE");}
  private void validateJson(String json){try{mapper.readTree(json==null?"{}":json);}catch(Exception e){throw bad("invalid_rules_json");}}
  private static ResponseStatusException bad(String s){return new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}private static ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"policy_not_found");}
  public record PolicyRequest(String name,String rulesJson){}
}
