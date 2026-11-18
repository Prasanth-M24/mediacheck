package com.mediacheck.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.mediacheck.auth.email.EmailDeliveryException;
import com.mediacheck.auth.email.EmailDeliveryProvider;

@Service
public class AccountEmailService {
  private static final Logger log = LoggerFactory.getLogger(AccountEmailService.class);
  private final EmailDeliveryProvider provider;
  private final boolean enabled;
  private final String publicAppUrl;

  public AccountEmailService(
      EmailDeliveryProvider provider,
      @Value("${mediacheck.email.enabled:false}") boolean enabled,
      @Value("${mediacheck.email.public-app-url:http://localhost:5173}") String publicAppUrl) {
    this.provider = provider;
    this.enabled = enabled;
    this.publicAppUrl = publicAppUrl.replaceAll("/$", "");
  }

  public boolean isConfigured() {
    return enabled && provider.isAvailable();
  }

  public void verification(String email, String token) {
    enqueue(email, "Verify your MEDIACHECK email", "Open this link to verify your email:\n" + link("/verify-email", token));
  }

  public void invitation(String email, String token) {
    enqueue(email, "MEDIACHECK company invitation", "Open this link to accept your company invitation:\n" + link("/auth/accept-invitation", token));
  }

  public void passwordReset(String email, String token) {
    enqueue(email, "Reset your MEDIACHECK password", "Open this link to reset your password:\n" + link("/forgot-password", token));
  }

  private String link(String path, String token) {
    return publicAppUrl + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
  }

  private void enqueue(String to, String subject, String body) {
    if (!enabled) {
      log.debug("Email delivery disabled by configuration; skipping notification");
      return;
    }
    Runnable send = () -> deliver(to, subject, body);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          send.run();
        }
      });
    } else {
      send.run();
    }
  }

  private void deliver(String to, String subject, String body) {
    try {
      provider.send(to, subject, body);
    } catch (EmailDeliveryException ex) {
      log.error("Account email delivery failed; token and recipient content suppressed");
      throw ex;
    }
  }
}
