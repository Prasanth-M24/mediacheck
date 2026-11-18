package com.mediacheck.auth.email;

public interface EmailDeliveryProvider {
  void send(String to, String subject, String body);
  boolean isAvailable();
}
