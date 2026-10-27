package com.company.crm.common.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * NFR-7.4a: queue and retry for outbound email. When the mail server can't take a message, the
 * whole MIME message (headers, Message-ID, attachments) is stored and retried with backoff —
 * 1, 5, 15, 60 and 180 minutes — then marked dead. A retry that gets through, or the final give-up,
 * is published so the customer timeline entry with the same Message-ID can be updated.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailOutboxService {

    /** Minutes to wait before attempt n+1 (index = attempts so far − 1). */
    static final int[] BACKOFF_MINUTES = {1, 5, 15, 60, 180};
    static final int MAX_ATTEMPTS = BACKOFF_MINUTES.length + 1;
    private static final int LEASE_MINUTES = 10;

    /** An email held for retry was finally delivered, or given up on. */
    public record Outcome(String messageId, boolean delivered, String error) {}

    private final MailOutboxRepository repository;
    private final JavaMailSender mailSender;
    private final ApplicationEventPublisher eventPublisher;

    /** Saves a message that failed its first attempt. Own transaction: kept even if the caller rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(MimeMessage message, Exception firstFailure) throws MessagingException {
        MailOutboxEntry entry = new MailOutboxEntry();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            message.writeTo(out); // also fixes the headers, keeping our Message-ID
            entry.setMime(out.toByteArray());
        } catch (IOException e) {
            throw new MessagingException("Could not store the email for retry", e);
        }
        entry.setMessageId(message.getMessageID());
        entry.setRecipients(message.getAllRecipients() == null ? "" : Arrays.stream(message.getAllRecipients())
                .map(Object::toString).collect(Collectors.joining(", ")));
        entry.setSubject(truncate(message.getSubject(), 500));
        entry.setLastError(truncate(firstFailure.getMessage(), 500));
        entry.setNextAttemptAt(LocalDateTime.now().plusMinutes(BACKOFF_MINUTES[0]));
        repository.save(entry);
        log.warn("Email to {} queued for retry: {}", entry.getRecipients(), firstFailure.getMessage());
    }

    @Transactional(readOnly = true)
    public List<Long> findDue() {
        return repository.findDueIds(LocalDateTime.now());
    }

    /** One retry, in its own transaction so each outcome is saved independently. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retry(Long id) {
        MailOutboxEntry entry = repository.findById(id).orElse(null);
        if (entry == null || !MailOutboxEntry.PENDING.equals(entry.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (repository.lease(id, entry.getNextAttemptAt(), now.plusMinutes(LEASE_MINUTES)) == 0) {
            return; // another instance is on it
        }
        entry = repository.findById(id).orElseThrow();
        try {
            mailSender.send(restore(entry.getMime()));
            entry.setStatus(MailOutboxEntry.SENT);
            entry.setSentAt(now);
            entry.setLastError(null);
            log.info("Queued email {} delivered on attempt {}", id, entry.getAttempts() + 1);
            entry.setAttempts(entry.getAttempts() + 1);
            repository.save(entry);
            eventPublisher.publishEvent(new Outcome(entry.getMessageId(), true, null));
        } catch (MailException | MessagingException e) {
            entry.setAttempts(entry.getAttempts() + 1);
            entry.setLastError(truncate(e.getMessage(), 500));
            if (entry.getAttempts() >= MAX_ATTEMPTS) {
                entry.setStatus(MailOutboxEntry.DEAD);
                log.warn("Giving up on email {} to {} after {} attempts: {}", id, entry.getRecipients(), entry.getAttempts(), e.getMessage());
                repository.save(entry);
                eventPublisher.publishEvent(new Outcome(entry.getMessageId(), false, entry.getLastError()));
            } else {
                entry.setNextAttemptAt(now.plusMinutes(BACKOFF_MINUTES[entry.getAttempts() - 1]));
                repository.save(entry);
            }
        }
    }

    /** Rebuilds the stored message without letting JavaMail assign a new Message-ID on send. */
    private MimeMessage restore(byte[] mime) throws MessagingException {
        Session session = mailSender instanceof JavaMailSenderImpl impl ? impl.getSession() : Session.getInstance(new Properties());
        return new MimeMessage(session, new ByteArrayInputStream(mime)) {
            @Override
            protected void updateMessageID() {
                // keep the original Message-ID (replies and the timeline entry refer to it)
            }
        };
    }

    private static String truncate(String v, int max) {
        return v == null ? null : v.length() > max ? v.substring(0, max) : v;
    }
}
