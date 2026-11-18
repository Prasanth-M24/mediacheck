package com.mediacheck.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException ex){
    Map<String,String> fields=new LinkedHashMap<>();
    ex.getBindingResult().getFieldErrors().forEach(error->fields.putIfAbsent(error.getField(),safeValidationMessage(error)));
    return ResponseEntity.badRequest().body(Map.of("error","invalid_request","fields",fields));
  }
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> status(ResponseStatusException ex){
    String reason=ex.getReason()==null?"request_failed":ex.getReason();
    return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error",reason));
  }
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict(DataIntegrityViolationException ex){return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","resource_conflict"));}
  @ExceptionHandler(CompanySelectionRequiredException.class)
  ResponseEntity<?> companySelection(CompanySelectionRequiredException ex){return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","company_selection_required","companies",ex.companies()));}
  private String safeValidationMessage(FieldError e){return e.getDefaultMessage()==null?"Invalid value":e.getDefaultMessage();}
}
