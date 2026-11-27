package com.company.crm.campaign.service;

import com.company.crm.campaign.entity.Campaign;
import com.company.crm.campaign.entity.CampaignRecipient;
import com.company.crm.campaign.repository.CampaignRecipientRepository;
import com.company.crm.campaign.repository.CampaignRepository;
import com.company.crm.common.enums.CampaignChannel;
import com.company.crm.common.enums.CampaignStatus;
import com.company.crm.common.enums.CommunicationStatus;
import com.company.crm.common.exception.ApiException;
import com.company.crm.communication.entity.Communication;
import com.company.crm.communication.service.CommunicationService;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.entity.CustomerContact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FR-7.1c/FR-7.4: actually sends email, SMS and (NOT-2) WhatsApp campaigns, and tracks what recipients do.
 * <ul>
 *   <li>Launch snapshots the segment into recipients — queued, or skipped (unsubscribed / no
 *       address) — and activates the campaign. Other channels just activate.</li>
 *   <li>A background sender works through the queue (paused/cancelled campaigns wait), logging
 *       every message on the customer's timeline.</li>
 *   <li>Emails carry a tracking pixel (opened), tracked links (clicked) and an unsubscribe link.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignDeliveryService {

    /** Messages sent per sender run. */
    private static final int BATCH = 200;
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"]+[^\\s<>\".,;:!?)]");
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final SegmentService segmentService;
    private final CommunicationService communicationService;

    @Value("${app.campaign.public-api-url}")
    private String publicApiUrl;

    public static boolean isSendable(CampaignChannel channel) {
        return channel == CampaignChannel.EMAIL || channel == CampaignChannel.SMS || channel == CampaignChannel.WHATSAPP;
    }

    /** What a sendable campaign needs before it can launch or be scheduled (no segment = activate only). */
    public static void assertReadyToSend(Campaign c) {
        if (!isSendable(c.getChannel()) || c.getSegment() == null) {
            return;
        }
        if (c.getMessageBody() == null || c.getMessageBody().isBlank()) {
            throw ApiException.badRequest("Write the message before launching");
        }
        if (c.getChannel() == CampaignChannel.EMAIL && (c.getMessageSubject() == null || c.getMessageSubject().isBlank())) {
            throw ApiException.badRequest("Add an email subject before launching");
        }
    }

    /** Activates the campaign and, for email/SMS/WhatsApp with a segment, queues one message per segment customer. */
    @Transactional
    public void launch(Campaign campaign) {
        assertReadyToSend(campaign);
        LocalDateTime now = LocalDateTime.now();
        if (isSendable(campaign.getChannel()) && campaign.getSegment() != null && campaign.getLaunchedAt() == null) {
            boolean email = campaign.getChannel() == CampaignChannel.EMAIL;
            for (Customer customer : segmentService.customers(campaign.getSegment())) {
                CampaignRecipient r = new CampaignRecipient();
                r.setTenant(campaign.getTenant());
                r.setCampaign(campaign);
                r.setCustomer(customer);
                r.setTrackingToken(newToken());
                r.setAddress(email ? emailOf(customer) : phoneOf(customer));
                // NOT-7: the customer's consent for the channel.
                if (customer.isMarketingOptOut()) {
                    skip(r, "Unsubscribed from marketing");
                } else if (campaign.getChannel() == CampaignChannel.SMS && customer.isSmsOptOut()) {
                    skip(r, "Opted out of SMS");
                } else if (campaign.getChannel() == CampaignChannel.WHATSAPP && !customer.isWhatsappOptIn()) {
                    skip(r, "Hasn't opted in to WhatsApp");
                } else if (r.getAddress() == null) {
                    skip(r, email ? "No email address on record" : "No phone number on record");
                }
                recipientRepository.save(r);
            }
        }
        campaign.setStatus(CampaignStatus.ACTIVE);
        if (campaign.getLaunchedAt() == null) {
            campaign.setLaunchedAt(now);
        }
        campaign.setScheduledAt(null);
        campaignRepository.save(campaign);
    }

    /** Recipients still waiting when a campaign is cancelled are not sent. */
    @Transactional
    public void cancelQueued(Campaign campaign) {
        recipientRepository.findByCampaignIdOrderByIdAsc(campaign.getId()).stream()
                .filter(r -> CampaignRecipient.QUEUED.equals(r.getStatus()))
                .forEach(r -> {
                    skip(r, "Campaign cancelled");
                    recipientRepository.save(r);
                });
    }

    @Transactional(readOnly = true)
    public List<Long> nextBatch() {
        return recipientRepository.findQueuedIds(CampaignStatus.ACTIVE, PageRequest.of(0, BATCH));
    }

    /** Sends one queued message in its own transaction (a failure is recorded, not thrown). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(Long recipientId) {
        if (recipientRepository.claimForSend(recipientId, LocalDateTime.now()) == 0) {
            return;
        }
        CampaignRecipient r = recipientRepository.findById(recipientId).orElseThrow();
        Campaign c = r.getCampaign();
        Communication result;
        if (c.getChannel() == CampaignChannel.EMAIL) {
            String unsubscribe = publicUrl("u", r.getTrackingToken());
            result = communicationService.sendMarketingEmail(c.getTenant(), r.getCustomer(), r.getAddress(), c.getMessageSubject(),
                    plainText(c, unsubscribe), html(c, r), unsubscribe);
        } else if (c.getChannel() == CampaignChannel.WHATSAPP) {
            result = communicationService.sendAutomatedWhatsApp(c.getTenant(), r.getCustomer(), r.getAddress(),
                    c.getMessageBody().trim() + "\nReply STOP to stop these messages.");
        } else {
            result = communicationService.sendAutomatedSms(c.getTenant(), r.getCustomer(), r.getAddress(),
                    c.getMessageBody().trim() + "\nReply STOP to opt out.");
        }
        if (result.getStatus() == CommunicationStatus.FAILED) {
            r.setStatus(CampaignRecipient.FAILED);
            r.setErrorMessage(truncate(result.getErrorMessage()));
        }
        recipientRepository.save(r);
    }

    // ==================== Tracking (public links) ====================

    /** FR-7.4: the open pixel was loaded. Unknown tokens are ignored. */
    @Transactional
    public void recordOpen(String token) {
        recipient(token).ifPresent(r -> {
            if (r.getOpenedAt() == null) {
                r.setOpenedAt(LocalDateTime.now());
                recipientRepository.save(r);
            }
        });
    }

    /** FR-7.4: a tracked link was clicked — returns where to send the browser (only links from the message itself). */
    @Transactional
    public Optional<String> recordClick(String token, int linkIndex) {
        return recipient(token).flatMap(r -> {
            List<String> links = links(r.getCampaign().getMessageBody());
            if (linkIndex < 0 || linkIndex >= links.size()) {
                return Optional.empty();
            }
            LocalDateTime now = LocalDateTime.now();
            if (r.getClickedAt() == null) {
                r.setClickedAt(now);
            }
            if (r.getOpenedAt() == null) {
                r.setOpenedAt(now); // a click implies the email was opened, even if images were blocked
            }
            recipientRepository.save(r);
            return Optional.of(links.get(linkIndex));
        });
    }

    /** FR-7.1c: the recipient unsubscribes — the customer gets no further campaigns. Returns the company name. */
    @Transactional
    public Optional<String> unsubscribe(String token) {
        return recipient(token).map(r -> {
            Customer customer = r.getCustomer();
            if (!customer.isMarketingOptOut()) {
                customer.setMarketingOptOut(true);
                customer.setMarketingOptOutAt(LocalDateTime.now());
            }
            return r.getTenant().getCompanyName();
        });
    }

    // ==================== Message building ====================

    /** The message as HTML: text escaped, links tracked, then the unsubscribe footer and the open pixel. */
    String html(Campaign c, CampaignRecipient r) {
        String token = r.getTrackingToken();
        int[] index = {0};
        String body = replaceLinks(c.getMessageBody(), url -> {
            String tracked = publicUrl("c", token) + "/" + index[0]++;
            return "<a href=\"" + HtmlUtils.htmlEscape(tracked) + "\">" + HtmlUtils.htmlEscape(url) + "</a>";
        }, HtmlUtils::htmlEscape).replace("\n", "<br>");
        return "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; color: #1f2937; line-height: 1.5;\">"
                + body
                + "<hr style=\"border: none; border-top: 1px solid #e5e7eb; margin: 24px 0 12px;\">"
                + "<p style=\"font-size: 12px; color: #6b7280;\">You are receiving this from " + HtmlUtils.htmlEscape(c.getTenant().getCompanyName())
                + ". <a href=\"" + HtmlUtils.htmlEscape(publicUrl("u", token)) + "\" style=\"color: #6b7280;\">Unsubscribe</a></p>"
                + "<img src=\"" + HtmlUtils.htmlEscape(publicUrl("o", token)) + "\" width=\"1\" height=\"1\" alt=\"\" style=\"display: block;\">"
                + "</div>";
    }

    private static String plainText(Campaign c, String unsubscribeUrl) {
        return c.getMessageBody().trim() + "\n\n--\nYou are receiving this from " + c.getTenant().getCompanyName()
                + ". Unsubscribe: " + unsubscribeUrl;
    }

    /** The links in a message, in order — what tracked link N points to. */
    static List<String> links(String body) {
        List<String> links = new ArrayList<>();
        if (body != null) {
            Matcher m = URL.matcher(body);
            while (m.find()) {
                links.add(m.group());
            }
        }
        return links;
    }

    private static String replaceLinks(String body, Function<String, String> link, Function<String, String> text) {
        StringBuilder out = new StringBuilder();
        Matcher m = URL.matcher(body);
        int last = 0;
        while (m.find()) {
            out.append(text.apply(body.substring(last, m.start()))).append(link.apply(m.group()));
            last = m.end();
        }
        return out.append(text.apply(body.substring(last))).toString();
    }

    private String publicUrl(String kind, String token) {
        String base = publicApiUrl.endsWith("/") ? publicApiUrl.substring(0, publicApiUrl.length() - 1) : publicApiUrl;
        return base + "/api/public/campaigns/" + kind + "/" + token;
    }

    // ==================== Helpers ====================

    private Optional<CampaignRecipient> recipient(String token) {
        return token == null || token.isBlank() ? Optional.empty() : recipientRepository.findByTrackingToken(token);
    }

    private static void skip(CampaignRecipient r, String reason) {
        r.setStatus(CampaignRecipient.SKIPPED);
        r.setErrorMessage(reason);
    }

    private static String emailOf(Customer c) {
        if (c.getEmail() != null && !c.getEmail().isBlank()) {
            return c.getEmail().trim();
        }
        return c.getContacts().stream().map(CustomerContact::getEmail).filter(e -> e != null && !e.isBlank()).findFirst().orElse(null);
    }

    private static String phoneOf(Customer c) {
        if (c.getPhone() != null && !c.getPhone().isBlank()) {
            return c.getPhone().trim();
        }
        return c.getContacts().stream().map(CustomerContact::getPhone).filter(p -> p != null && !p.isBlank()).findFirst().orElse(null);
    }

    private static String truncate(String value) {
        return value == null ? null : value.length() > 500 ? value.substring(0, 500) : value;
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
