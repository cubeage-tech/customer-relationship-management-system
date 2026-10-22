package com.company.crm.common.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    /** FR-5.1: From address for customer emails (blank = the SMTP account default). */
    @Value("${app.communication.email.from:}")
    private String customerEmailFrom;

    /** FR-5.1: the shared mailbox polled over IMAP, so customer replies come back into the CRM. */
    @Value("${app.communication.email.reply-to:}")
    private String customerEmailReplyTo;

    /**
     * FR-5.1: sends a plain-text email to a customer with our own Message-ID, so a reply's
     * In-Reply-To header can be matched back to the logged email. Throws on failure — the caller
     * records the send as failed.
     */
    public void sendCustomerEmail(String toEmail, String subject, String body, String messageId) throws MessagingException {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties())) {
            @Override
            protected void updateMessageID() throws MessagingException {
                setHeader("Message-ID", messageId);
            }
        };
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(body, false);
        if (!customerEmailFrom.isBlank()) {
            helper.setFrom(customerEmailFrom);
        }
        if (!customerEmailReplyTo.isBlank()) {
            helper.setReplyTo(customerEmailReplyTo);
        }
        mailSender.send(message);
    }

    /** Sends the "confirm your email" message with a button linking back to the frontend. */
    public void sendVerificationEmail(String toEmail, String fullName, String token) {
        String verifyUrl = frontendUrl + "/verify-email?token=" + token;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Verify your SmartCRM AI account");
            helper.setText(buildVerificationEmailHtml(fullName, verifyUrl), true);
            mailSender.send(message);
        } catch (MailException | MessagingException e) {
            // The account was already created — a mail outage shouldn't fail signup itself,
            // but the user has no way to verify without this link, so this needs to be loud.
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
        }
    }

    /** Sends the "reset your password" message with a button linking to the frontend reset page. */
    public void sendPasswordResetEmail(String toEmail, String fullName, String token) {
        String resetUrl = frontendUrl + "/reset-password?token=" + token;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Reset your SmartCRM AI password");
            helper.setText(buildPasswordResetEmailHtml(fullName, resetUrl), true);
            mailSender.send(message);
        } catch (MailException | MessagingException e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildPasswordResetEmailHtml(String fullName, String resetUrl) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; padding: 32px; color: #1f2937;">
                  <h2 style="color: #1e1b4b; margin-bottom: 4px;">Reset your password</h2>
                  <p>Hi %s,</p>
                  <p>We received a request to reset your SmartCRM AI password. If it wasn't you, you can ignore this email.</p>
                  <p style="text-align: center; margin: 32px 0;">
                    <a href="%s"
                       style="background:#6366f1;color:#ffffff;padding:12px 28px;border-radius:8px;
                              text-decoration:none;font-weight:600;display:inline-block;">
                      Reset password
                    </a>
                  </p>
                  <p style="color:#6b7280;font-size:13px;">
                    This link expires in 1 hour. If the button doesn't work, copy this link into your browser:<br>
                    <a href="%s" style="color:#6366f1;">%s</a>
                  </p>
                </div>
                """.formatted(fullName, resetUrl, resetUrl, resetUrl);
    }

    private String buildVerificationEmailHtml(String fullName, String verifyUrl) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; padding: 32px; color: #1f2937;">
                  <h2 style="color: #1e1b4b; margin-bottom: 4px;">Welcome to SmartCRM AI</h2>
                  <p>Hi %s,</p>
                  <p>Confirm your email address to activate your workspace and sign in.</p>
                  <p style="text-align: center; margin: 32px 0;">
                    <a href="%s"
                       style="background:#6366f1;color:#ffffff;padding:12px 28px;border-radius:8px;
                              text-decoration:none;font-weight:600;display:inline-block;">
                      Verify email
                    </a>
                  </p>
                  <p style="color:#6b7280;font-size:13px;">
                    This link expires in 48 hours. If the button doesn't work, copy this link into your browser:<br>
                    <a href="%s" style="color:#6366f1;">%s</a>
                  </p>
                </div>
                """.formatted(fullName, verifyUrl, verifyUrl, verifyUrl);
    }
}
