package com.company.crm.campaign.controller;

import com.company.crm.campaign.service.CampaignDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.util.Base64;

/**
 * FR-7.4/FR-7.1c: links inside campaign emails — the open pixel, tracked links and unsubscribe.
 * Public by design: the recipient's unguessable tracking token in the path is the credential
 * (see Security and EndpointAuthorizationCoverageTest).
 */
@RestController
@RequestMapping("/api/public/campaigns")
@RequiredArgsConstructor
public class CampaignTrackingController {

    /** A transparent 1x1 GIF. */
    private static final byte[] PIXEL = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");

    private final CampaignDeliveryService deliveryService;

    @GetMapping("/o/{token}")
    @Operation(summary = "FR-7.4: open-tracking pixel")
    public ResponseEntity<byte[]> open(@PathVariable String token) {
        deliveryService.recordOpen(token);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_GIF).cacheControl(CacheControl.noStore()).body(PIXEL);
    }

    @GetMapping("/c/{token}/{link}")
    @Operation(summary = "FR-7.4: tracked link — records the click and redirects to the link in the message")
    public ResponseEntity<Void> click(@PathVariable String token, @PathVariable int link) {
        return deliveryService.recordClick(token, link)
                .map(url -> ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, url).<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** A confirmation page (mail scanners prefetch links, so unsubscribing needs the button's POST). */
    @GetMapping(value = "/u/{token}", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "FR-7.1c: unsubscribe confirmation page")
    public String unsubscribePage(@PathVariable String token) {
        return page("Unsubscribe", "<p>Stop receiving marketing emails and messages from us?</p>"
                + "<form method=\"post\"><button type=\"submit\" style=\"padding:10px 20px;border:0;border-radius:6px;"
                + "background:#4f46e5;color:#fff;font-size:15px;cursor:pointer;\">Unsubscribe</button></form>");
    }

    /** The page's button, and one-click unsubscribe from mail apps (List-Unsubscribe-Post). */
    @PostMapping(value = "/u/{token}", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "FR-7.1c: unsubscribe from marketing")
    public ResponseEntity<String> unsubscribe(@PathVariable String token) {
        return deliveryService.unsubscribe(token)
                .map(company -> ResponseEntity.ok(page("Unsubscribed",
                        "<p>You will no longer receive marketing messages from " + HtmlUtils.htmlEscape(company) + ".</p>")))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(page("Link not valid",
                        "<p>This unsubscribe link is not valid.</p>")));
    }

    private static String page(String title, String content) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">"
                + "<title>" + title + "</title></head><body style=\"font-family:Arial,sans-serif;max-width:480px;margin:60px auto;"
                + "padding:0 16px;color:#1f2937;\"><h1 style=\"font-size:22px;\">" + title + "</h1>" + content + "</body></html>";
    }
}
