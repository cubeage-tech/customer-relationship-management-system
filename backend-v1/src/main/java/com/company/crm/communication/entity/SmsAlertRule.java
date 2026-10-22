package com.company.crm.communication.entity;

import com.company.crm.common.enums.SmsAlertEvent;
import com.company.crm.common.enums.SmsAlertRecipient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/** FR-5.3: whether an event sends an SMS alert for a tenant, and to whom. Missing row = disabled. */
@Entity
@Table(name = "sms_alert_rules")
@IdClass(SmsAlertRule.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class SmsAlertRule {

    @Id
    @Column(name = "tenant_id")
    private Long tenantId;

    /** Stored as the event's db value — JPA converters are not applied to id columns. */
    @Id
    @Column(name = "event", nullable = false)
    private String eventCode;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private SmsAlertRecipient recipient = SmsAlertRecipient.CUSTOMER;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public SmsAlertEvent getEvent() {
        return SmsAlertEvent.fromDbValue(eventCode);
    }

    public void setEvent(SmsAlertEvent event) {
        this.eventCode = event.getDbValue();
    }

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private Long tenantId;
        private String eventCode;

        public Key(Long tenantId, SmsAlertEvent event) {
            this.tenantId = tenantId;
            this.eventCode = event.getDbValue();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(tenantId, k.tenantId) && Objects.equals(eventCode, k.eventCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, eventCode);
        }
    }
}
