package com.company.crm.common.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
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
        sendCustomerEmail(toEmail, subject, body, messageId, null);
    }

    /** FR-5.5c: as above, with an iCalendar invite attached when {@code ics} is not null. */
    public void sendCustomerEmail(String toEmail, String subject, String body, String messageId, String ics) throws MessagingException {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties())) {
            @Override
            protected void updateMessageID() throws MessagingException {
                setHeader("Message-ID", messageId);
            }
        };
        MimeMessageHelper helper = new MimeMessageHelper(message, ics != null, "UTF-8");
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(body, false);
        if (ics != null) {
            attachIcs(helper, ics);
        }
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

    /**
     * FR-5.5: a plain-text email to a CRM user (meeting invite, update or reminder), with an
     * iCalendar attachment when {@code ics} is not null. Throws on failure so the caller can log it.
     */
    public void sendCalendarEmail(String toEmail, String subject, String body, String ics) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, ics != null, "UTF-8");
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(body, false);
        if (ics != null) {
            attachIcs(helper, ics);
        }
        mailSender.send(message);
    }

    /**
     * FR-7.1c: a marketing email — HTML with a plain-text alternative, our own Message-ID, and a
     * List-Unsubscribe header so mail apps offer one-click opt-out. Throws on failure.
     */
    public void sendMarketingEmail(String toEmail, String subject, String text, String html, String messageId,
                                   String unsubscribeUrl) throws MessagingException {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties())) {
            @Override
            protected void updateMessageID() throws MessagingException {
                setHeader("Message-ID", messageId);
            }
        };
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(text, html);
        if (!customerEmailFrom.isBlank()) {
            helper.setFrom(customerEmailFrom);
        }
        if (!customerEmailReplyTo.isBlank()) {
            helper.setReplyTo(customerEmailReplyTo);
        }
        message.setHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");
        message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        mailSender.send(message);
    }

    private static void attachIcs(MimeMessageHelper helper, String ics) throws MessagingException {
        String method = ics.contains("METHOD:CANCEL") ? "CANCEL" : "REQUEST";
        helper.addAttachment("invite.ics", new ByteArrayResource(ics.getBytes(StandardCharsets.UTF_8)),
                "text/calendar; charset=UTF-8; method=" + method);
    }

    /**
     * FR-2.5: tells a salesperson that a lead's follow-up is due, with a link to the lead. Throws on
     * failure so the caller can log it.
     */
    public void sendFollowUpReminderEmail(String toEmail, String fullName, String leadName, String companyName,
                                          String dueAt, Long leadId) throws MessagingException {
        String leadUrl = frontendUrl + "/leads/" + leadId;
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        helper.setTo(toEmail);
        helper.setSubject("Follow-up due: " + leadName + " (" + companyName + ")");
        helper.setText("""
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; padding: 32px; color: #1f2937;">
                  <h2 style="color: #1e1b4b; margin-bottom: 4px;">Follow-up due</h2>
                  <p>Hi %s,</p>
                  <p>Your follow-up with <strong>%s</strong> at <strong>%s</strong> was due on %s.</p>
                  <p style="text-align: center; margin: 32px 0;">
                    <a href="%s"
                       style="background:#6366f1;color:#ffffff;padding:12px 28px;border-radius:8px;
                              text-decoration:none;font-weight:600;display:inline-block;">
                      Open lead
                    </a>
                  </p>
                  <p style="color:#6b7280;font-size:13px;">Set a new follow-up date on the lead to get another reminder.</p>
                </div>
                """.formatted(escape(fullName), escape(leadName), escape(companyName), dueAt, leadUrl), true);
        mailSender.send(message);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
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
