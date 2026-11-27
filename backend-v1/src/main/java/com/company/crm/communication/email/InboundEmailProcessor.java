package com.company.crm.communication.email;

import com.company.crm.common.enums.CommunicationChannel;
import com.company.crm.communication.service.CommunicationService;
import com.company.crm.communication.service.CommunicationService.InboundMessage;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** FR-5.1: turns one received email into a timeline entry (matching is done by CommunicationService). */
@Component
@RequiredArgsConstructor
public class InboundEmailProcessor {

    private static final int MAX_BODY_LENGTH = 20000;

    private final CommunicationService communicationService;

    /** Our own sending address — mail from it (bounces, loops) is never logged as a customer reply. */
    @Value("${app.communication.email.from:}")
    private String ownAddress;

    /** @return true if the email was logged against a customer/lead. */
    public boolean process(MimeMessage message) throws MessagingException, IOException {
        Address[] from = message.getFrom();
        if (from == null || from.length == 0 || !(from[0] instanceof InternetAddress sender)) {
            return false;
        }
        String address = sender.getAddress();
        if (address == null || address.equalsIgnoreCase(ownAddress)) {
            return false;
        }
        List<String> replyTo = new ArrayList<>();
        addHeaderIds(message.getHeader("In-Reply-To"), replyTo);
        addHeaderIds(message.getHeader("References"), replyTo);
        LocalDateTime sentAt = message.getSentDate() != null
                ? LocalDateTime.ofInstant(message.getSentDate().toInstant(), ZoneId.systemDefault())
                : null;
        return communicationService.recordInbound(new InboundMessage(
                CommunicationChannel.EMAIL, address, message.getSubject(), truncate(textOf(message)),
                message.getMessageID(), replyTo, sentAt)).isPresent();
    }

    private static void addHeaderIds(String[] headers, List<String> into) {
        if (headers == null) {
            return;
        }
        for (String header : headers) {
            Arrays.stream(header.trim().split("\\s+")).filter(id -> !id.isBlank()).forEach(into::add);
        }
    }

    /** Plain-text part if present, otherwise the HTML part with tags stripped. */
    private static String textOf(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            return String.valueOf(part.getContent());
        }
        if (part.isMimeType("text/html")) {
            return String.valueOf(part.getContent()).replaceAll("(?s)<[^>]*>", " ").replaceAll("[ \\t]+", " ").trim();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            String html = null;
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(child.getDisposition())) {
                    continue;
                }
                String text = textOf(child);
                if (child.isMimeType("text/plain") && text != null) {
                    return text;
                }
                if (html == null) {
                    html = text;
                }
            }
            return html;
        }
        return null;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_BODY_LENGTH ? value : value.substring(0, MAX_BODY_LENGTH);
    }
}
