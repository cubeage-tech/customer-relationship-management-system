package com.company.crm.communication.controller;

import com.company.crm.common.enums.CommunicationChannel;
import com.company.crm.communication.integration.TwilioClient;
import com.company.crm.communication.service.CommunicationService;
import com.company.crm.communication.service.CommunicationService.InboundMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Called by Twilio, not the browser — public, but every request must carry a valid
 * X-Twilio-Signature for the configured auth token (otherwise 403).
 * Configure in Twilio: incoming messages → {base}/api/communications/webhooks/twilio/inbound.
 */
@RestController
@RequestMapping("/api/communications/webhooks/twilio")
@RequiredArgsConstructor
@Slf4j
public class TwilioWebhookController {

    private static final String EMPTY_TWIML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>";

    private final TwilioClient twilioClient;
    private final CommunicationService communicationService;

    /** FR-5.2: an incoming WhatsApp (or SMS) message from a customer. */
    @PostMapping(value = "/inbound", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> inbound(
            @RequestParam Map<String, String> params,
            @RequestHeader(value = "X-Twilio-Signature", required = false) String signature) {
        if (!twilioClient.isValidSignature("/api/communications/webhooks/twilio/inbound", params, signature)) {
            log.warn("Rejected Twilio inbound webhook with an invalid signature");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String from = params.getOrDefault("From", "");
        CommunicationChannel channel = from.startsWith("whatsapp:") ? CommunicationChannel.WHATSAPP : CommunicationChannel.SMS;
        communicationService.recordInbound(new InboundMessage(
                channel, from, null, params.get("Body"), params.get("MessageSid"), List.of(), null));
        return ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(EMPTY_TWIML);
    }

    /** FR-5.2/5.3: delivery status updates for messages we sent (StatusCallback). */
    @PostMapping(value = "/status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> status(
            @RequestParam Map<String, String> params,
            @RequestHeader(value = "X-Twilio-Signature", required = false) String signature) {
        if (!twilioClient.isValidSignature(TwilioClient.STATUS_CALLBACK_PATH, params, signature)) {
            log.warn("Rejected Twilio status webhook with an invalid signature");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (params.get("MessageSid") != null) {
            communicationService.updateDeliveryStatus(params.get("MessageSid"), params.get("MessageStatus"), params.get("ErrorCode"));
        }
        return ResponseEntity.noContent().build();
    }
}
