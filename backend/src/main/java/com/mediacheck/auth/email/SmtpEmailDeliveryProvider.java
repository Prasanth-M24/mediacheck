package com.mediacheck.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailDeliveryProvider implements EmailDeliveryProvider {
  private static final Logger log = LoggerFactory.getLogger(SmtpEmailDeliveryProvider.class);
  private final ObjectProvider<JavaMailSender> sender;
  private final boolean enabled;
  private final String from;

  public SmtpEmailDeliveryProvider(
      ObjectProvider<JavaMailSender> sender,
      @Value("${mediacheck.email.enabled:false}") boolean enabled,
      @Value("${mediacheck.email.from:}") String from) {
    this.sender = sender;
    this.enabled = enabled;
    this.from = from != null ? from.trim() : "";
  }

  @Override
  public boolean isAvailable() {
    return enabled && !from.isBlank() && sender.getIfAvailable() != null;
  }

  @Override
  public void send(String to, String subject, String body) {
    JavaMailSender mail = sender.getIfAvailable();
    if (mail == null || from.isBlank()) {
      throw new EmailDeliveryException("SMTP mail transport is not configured or sender address is empty", null);
    }
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(from);
      message.setTo(to);
      message.setSubject(subject);
      message.setText(body);
      mail.send(message);
      log.info("Email dispatched successfully via SMTP transport (details suppressed)");
    } catch (Exception ex) {
      log.error("Email delivery failed via SMTP transport: {}", ex.getClass().getSimpleName());
      throw new EmailDeliveryException("Failed to send email via SMTP", ex);
    }
  }
}
