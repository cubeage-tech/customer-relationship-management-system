package com.company.crm.notification.settings;

import com.company.crm.common.response.Response;
import com.company.crm.notification.delivery.NotificationDeliveryLog;
import com.company.crm.notification.delivery.NotificationDeliveryLog.DeliveryRes;
import com.company.crm.notification.settings.NotificationSettingsService.ChannelUpdate;
import com.company.crm.notification.settings.NotificationSettingsService.EventSettings;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** NOT-6 (which events notify, by which channels) and NOT-8 (the delivery log) for the tenant admin. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationSettingsController {

    private final NotificationSettingsService settingsService;
    private final NotificationDeliveryLog deliveryLog;

    @GetMapping("/notification-settings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "NOT-6: each notification event and its channels (in-app, email, SMS, WhatsApp)")
    public Response<List<EventSettings>> settings(@AuthenticationPrincipal User currentUser) {
        return Response.ok(settingsService.list(currentUser));
    }

    @PutMapping("/notification-settings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "NOT-6: switch event channels on or off (and choose the SMS recipient)")
    public Response<List<EventSettings>> update(@AuthenticationPrincipal User currentUser, @RequestBody List<ChannelUpdate> changes) {
        return Response.ok("Notification settings saved", settingsService.update(currentUser, changes));
    }

    @GetMapping("/notification-deliveries")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "NOT-8: every notification sent, by channel, with its delivery status (100 per page)")
    public Response<List<DeliveryRes>> deliveries(@AuthenticationPrincipal User currentUser,
                                                  @RequestParam(required = false) String channel,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String event,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(defaultValue = "0") int page) {
        return Response.ok(deliveryLog.list(currentUser, channel, status, event, from, to, page));
    }
}
