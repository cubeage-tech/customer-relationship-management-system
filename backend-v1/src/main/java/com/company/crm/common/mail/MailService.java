package com.company.crm.common.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

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
