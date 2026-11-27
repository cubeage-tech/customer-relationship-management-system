package com.company.crm.communication.email;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.company.crm.integration.calllog.IntegrationCallLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * FR-5.1 receive side: polls the shared mailbox (the Reply-To of customer emails) over IMAPS and
 * logs unread messages as inbound emails. Only active with IMAP_ENABLED=true. Each message is
 * marked read after processing; the unique Message-ID makes a re-poll harmless.
 */
@Component
@ConditionalOnProperty(name = "app.communication.email.imap.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class InboundEmailPoller {

    private final InboundEmailProcessor processor;
    private final IntegrationCallLog callLog;

    @Value("${app.communication.email.imap.host}")
    private String host;

    @Value("${app.communication.email.imap.port:993}")
    private int port;

    @Value("${app.communication.email.imap.username}")
    private String username;

    @Value("${app.communication.email.imap.password}")
    private String password;

    @Value("${app.communication.email.imap.folder:INBOX}")
    private String folderName;

    @Scheduled(fixedDelayString = "${app.communication.email.imap.poll-interval-ms:60000}")
    public void poll() {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "20000");
        IntegrationCallLog.Call call = IntegrationCallLog.start(callLog, "imap", "inbox.poll");
        try (Store store = Session.getInstance(props).getStore("imaps")) {
            store.connect(host, port, username, password);
            Folder folder = store.getFolder(folderName);
            folder.open(Folder.READ_WRITE);
            try {
                int logged = 0;
                for (Message message : folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false))) {
                    try {
                        if (processor.process((MimeMessage) message)) {
                            logged++;
                        }
                    } catch (Exception e) {
                        log.warn("Skipping inbound email that could not be processed: {}", e.getMessage());
                    }
                    message.setFlag(Flags.Flag.SEEN, true);
                }
                if (logged > 0) {
                    log.info("Inbound email poll: {} message(s) logged", logged);
                }
            } finally {
                folder.close(false);
            }
            call.ok(null);
        } catch (Exception e) {
            log.error("Inbound email poll failed: {}", e.getMessage());
            call.failed(null, e.getMessage());
        }
    }
}
