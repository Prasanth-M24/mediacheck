package com.mediacheck.auth.email;

public class EmailDeliveryException extends RuntimeException {
  public EmailDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}
