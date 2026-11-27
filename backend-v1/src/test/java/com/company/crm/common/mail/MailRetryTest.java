package com.company.crm.common.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** NFR-7.4a: failed emails are kept whole and retried with backoff; permanent rejections aren't. */
class MailRetryTest {

    private JavaMailSender mailSender;
    private MailOutboxRepository repository;
    private ApplicationEventPublisher events;
    private MailOutboxService outbox;
    private MailService mailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        repository = mock(MailOutboxRepository.class);
        events = mock(ApplicationEventPublisher.class);
        outbox = spy(new MailOutboxService(repository, mailSender, events));
        mailService = new MailService(mailSender, outbox);
        ReflectionTestUtils.setField(mailService, "frontendUrl", "http://crm.test");
    }

    @Test
    void anUnreachableMailServerQueuesTheWholeMessage() throws Exception {
        doThrow(new MailSendException("Mail server connection failed")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> mailService.sendCalendarEmail("organizer@crm.test", "Meeting", "Body", "BEGIN:VCALENDAR\nEND:VCALENDAR"))
                .isInstanceOf(MailQueuedException.class);

        ArgumentCaptor<MailOutboxEntry> saved = ArgumentCaptor.forClass(MailOutboxEntry.class);
        verify(repository).save(saved.capture());
        MailOutboxEntry entry = saved.getValue();
        assertThat(entry.getRecipients()).isEqualTo("organizer@crm.test");
        assertThat(entry.getSubject()).isEqualTo("Meeting");
        assertThat(new String(entry.getMime())).contains("Subject: Meeting").contains("invite.ics");
        assertThat(entry.getNextAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(entry.getStatus()).isEqualTo(MailOutboxEntry.PENDING);
    }

    @Test
    void anInvalidRecipientIsNotRetried() {
        SendFailedException rejected = new SendFailedException("550 no such user", null, new InternetAddress[0],
                new InternetAddress[0], new jakarta.mail.Address[]{addr("nobody@crm.test")});
        doThrow(new MailSendException(Map.of(new Object(), rejected))).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> mailService.sendCalendarEmail("nobody@crm.test", "Meeting", "Body", null))
                .isInstanceOf(MailSendException.class)
                .isNotInstanceOf(MailQueuedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void aRetryKeepsTheOriginalMessageIdAndReportsDelivery() throws Exception {
        MailOutboxEntry entry = entry("<crm.42.abc@smartcrm>", 1);
        when(repository.findById(7L)).thenReturn(Optional.of(entry));
        when(repository.lease(eq(7L), any(), any())).thenReturn(1);

        outbox.retry(7L);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        sent.getValue().saveChanges(); // what JavaMailSender does before sending
        assertThat(sent.getValue().getMessageID()).isEqualTo("<crm.42.abc@smartcrm>");
        assertThat(entry.getStatus()).isEqualTo(MailOutboxEntry.SENT);
        verify(events).publishEvent(new MailOutboxService.Outcome("<crm.42.abc@smartcrm>", true, null));
    }

    @Test
    void failedRetriesBackOffThenGiveUp() throws Exception {
        MailOutboxEntry entry = entry("<m@x>", 1);
        when(repository.findById(7L)).thenReturn(Optional.of(entry));
        when(repository.lease(eq(7L), any(), any())).thenReturn(1);
        doThrow(new MailSendException("still down")).when(mailSender).send(any(MimeMessage.class));

        outbox.retry(7L);
        assertThat(entry.getAttempts()).isEqualTo(2);
        assertThat(entry.getStatus()).isEqualTo(MailOutboxEntry.PENDING);
        assertThat(entry.getNextAttemptAt()).isAfter(LocalDateTime.now().plusMinutes(4));

        entry.setAttempts(MailOutboxService.MAX_ATTEMPTS - 1);
        outbox.retry(7L);
        assertThat(entry.getStatus()).isEqualTo(MailOutboxEntry.DEAD);
        verify(events).publishEvent(new MailOutboxService.Outcome("<m@x>", false, "still down"));
    }

    @Test
    void anotherInstanceHoldingTheLeaseMeansNoSend() {
        when(repository.findById(7L)).thenReturn(Optional.of(entry("<m@x>", 1)));
        when(repository.lease(anyLong(), any(), any())).thenReturn(0);
        outbox.retry(7L);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void razorpayRefusalsAreNotRetriedButOtherErrorsAre() {
        assertThat(com.company.crm.payment.gateway.RazorpayPaymentGateway.isTransient(
                new com.razorpay.RazorpayException("BAD_REQUEST_ERROR:amount must be at least INR 1.00"))).isFalse();
        assertThat(com.company.crm.payment.gateway.RazorpayPaymentGateway.isTransient(
                new com.razorpay.RazorpayException("java.net.SocketTimeoutException: Read timed out"))).isTrue();
    }

    private static MailOutboxEntry entry(String messageId, int attempts) {
        try {
            MimeMessage m = new MimeMessage(Session.getInstance(new Properties())) {
                @Override
                protected void updateMessageID() throws MessagingException {
                    setHeader("Message-ID", messageId);
                }
            };
            m.setRecipients(jakarta.mail.Message.RecipientType.TO, "to@crm.test");
            m.setSubject("Hello");
            m.setText("Body");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            m.writeTo(out);
            MailOutboxEntry e = new MailOutboxEntry();
            e.setMessageId(messageId);
            e.setRecipients("to@crm.test");
            e.setMime(out.toByteArray());
            e.setAttempts(attempts);
            e.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
            return e;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static InternetAddress addr(String v) {
        try {
            return new InternetAddress(v);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
