package com.mediacheck.auth;
import java.util.List;
import com.mediacheck.auth.AuthDtos.SafeCompany;
public class CompanySelectionRequiredException extends RuntimeException {
  private final List<SafeCompany> companies;
  public CompanySelectionRequiredException(List<SafeCompany> companies){super("company_selection_required");this.companies=companies;}
  public List<SafeCompany> companies(){return companies;}
}
