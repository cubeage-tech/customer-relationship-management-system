package com.company.crm.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** FR-M.4 shapes: the inbox, manager messages and device registration. */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationRes(Long id, String type, String title, String body, String entityType, Long entityId,
                                  Long senderId, String senderName, boolean read, LocalDateTime createdAt) {}

    public record Inbox(long unread, List<NotificationRes> items) {}

    /** A manager's message to some of their people (userIds) and/or a whole team (teamId). */
    @Getter
    @Setter
    public static class MessageReq {
        @NotBlank
        @Size(max = 200)
        private String title;
        @NotBlank
        @Size(max = 2000)
        private String body;
        @Size(max = 200)
        private List<Long> userIds;
        private Long teamId;
    }

    public record MessageRes(int recipients) {}

    @Getter
    @Setter
    public static class DeviceReq {
        @NotBlank
        @Size(max = 512)
        private String token;
        /** android | ios */
        @NotBlank
        private String platform;
    }
}
