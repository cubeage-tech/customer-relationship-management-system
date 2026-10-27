package com.company.crm.common.mail;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** INT-2: campaign emails can go out from their own sender, with one-click unsubscribe headers. */
class MarketingEmailTest {

    private JavaMailSender mailSender;
    private MailService mailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        mailService = new MailService(mailSender, new MailOutboxService(mock(MailOutboxRepository.class), mailSender,
                mock(ApplicationEventPublisher.class)));
        ReflectionTestUtils.setField(mailService, "customerEmailFrom", "sales@crm.test");
        ReflectionTestUtils.setField(mailService, "customerEmailReplyTo", "");
    }

    @Test
    void campaignsUseTheMarketingSenderWhenOneIsSet() throws Exception {
        assertThat(send().getFrom()).containsExactly(new InternetAddress("sales@crm.test"));

        ReflectionTestUtils.setField(mailService, "marketingEmailFrom", "news@crm.test");
        MimeMessage message = send();
        assertThat(message.getFrom()).containsExactly(new InternetAddress("news@crm.test"));
        assertThat(message.getHeader("List-Unsubscribe")).containsExactly("<https://crm.test/u/abc>");
        assertThat(message.getHeader("List-Unsubscribe-Post")).containsExactly("List-Unsubscribe=One-Click");
    }

    private MimeMessage send() throws Exception {
        org.mockito.Mockito.clearInvocations(mailSender);
        mailService.sendMarketingEmail("buyer@customer.test", "Spring offer", "Text", "<p>Text</p>", "<m1@crm.test>",
                "https://crm.test/u/abc");
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        return sent.getValue();
    }
}
