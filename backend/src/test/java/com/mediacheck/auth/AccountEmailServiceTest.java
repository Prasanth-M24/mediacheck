package com.mediacheck.auth;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import com.mediacheck.auth.email.EmailDeliveryException;
import com.mediacheck.auth.email.EmailDeliveryProvider;
import com.mediacheck.auth.email.SmtpEmailDeliveryProvider;

class AccountEmailServiceTest {
  @Test
  void sendsVerificationInvitationAndResetLinksWithoutLoggingContent() {
    EmailDeliveryProvider provider = mock(EmailDeliveryProvider.class);
    when(provider.isAvailable()).thenReturn(true);
    AccountEmailService emails = new AccountEmailService(provider, true, "https://app.example.test/");

    emails.verification("verify@example.test", "verify-secret");
    emails.invitation("invite@example.test", "invite-secret");
    emails.passwordReset("reset@example.test", "reset-secret");

    verify(provider).send(eq("verify@example.test"), eq("Verify your MEDIACHECK email"), contains("https://app.example.test/verify-email?token=verify-secret"));
    verify(provider).send(eq("invite@example.test"), eq("MEDIACHECK company invitation"), contains("https://app.example.test/auth/accept-invitation?token=invite-secret"));
    verify(provider).send(eq("reset@example.test"), eq("Reset your MEDIACHECK password"), contains("https://app.example.test/forgot-password?token=reset-secret"));
  }

  @Test
  void disabledDeliveryDoesNotRequestTransport() {
    EmailDeliveryProvider provider = mock(EmailDeliveryProvider.class);
    AccountEmailService emails = new AccountEmailService(provider, false, "http://localhost:5173");
    emails.verification("person@example.test", "local-token");
    verifyNoInteractions(provider);
  }

  @Test
  void deliveryFailureThrowsEmailDeliveryException() {
    EmailDeliveryProvider provider = mock(EmailDeliveryProvider.class);
    doThrow(new EmailDeliveryException("SMTP error", new RuntimeException("connection refused")))
        .when(provider).send(anyString(), anyString(), anyString());
    AccountEmailService emails = new AccountEmailService(provider, true, "http://localhost:5173");

    assertThatThrownBy(() -> emails.verification("person@example.test", "token-fail"))
        .isInstanceOf(EmailDeliveryException.class)
        .hasMessageContaining("SMTP error");
  }

  @Test
  void smtpEmailDeliveryProviderBuildsAndDispatchesMessage() {
    @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> mailProvider = mock(ObjectProvider.class);
    JavaMailSender sender = mock(JavaMailSender.class);
    when(mailProvider.getIfAvailable()).thenReturn(sender);

    SmtpEmailDeliveryProvider smtp = new SmtpEmailDeliveryProvider(mailProvider, true, "noreply@mediacheck.test");
    assertThat(smtp.isAvailable()).isTrue();

    smtp.send("user@example.test", "Test Subject", "Test Body Content");

    ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(sender).send(messageCaptor.capture());
    SimpleMailMessage sent = messageCaptor.getValue();
    assertThat(sent.getFrom()).isEqualTo("noreply@mediacheck.test");
    assertThat(sent.getTo()).containsExactly("user@example.test");
    assertThat(sent.getSubject()).isEqualTo("Test Subject");
    assertThat(sent.getText()).isEqualTo("Test Body Content");
  }

  @Test
  void smtpEmailDeliveryProviderTranslatesExceptions() {
    @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> mailProvider = mock(ObjectProvider.class);
    JavaMailSender sender = mock(JavaMailSender.class);
    when(mailProvider.getIfAvailable()).thenReturn(sender);
    doThrow(new MailSendException("SMTP connection timeout")).when(sender).send(any(SimpleMailMessage.class));

    SmtpEmailDeliveryProvider smtp = new SmtpEmailDeliveryProvider(mailProvider, true, "noreply@mediacheck.test");
    assertThatThrownBy(() -> smtp.send("user@example.test", "Test", "Body"))
        .isInstanceOf(EmailDeliveryException.class)
        .hasMessageContaining("Failed to send email via SMTP");
  }
}
