package com.company.crm.communication.entity;

import com.company.crm.common.enums.CommunicationChannel;
import com.company.crm.common.enums.CommunicationDirection;
import com.company.crm.customer.entity.Customer;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "communication_logs")
@Getter
@Setter
@NoArgsConstructor
public class CommunicationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false, length = 20)
    private CommunicationChannel channel;

    @Column(nullable = false, length = 20)
    private CommunicationDirection direction;

    @Column(length = 255)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "contact_name", length = 150)
    private String contactName;

    @Column(name = "contact_address", length = 255)
    private String contactAddress;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
        createdAt = LocalDateTime.now();
    }
}
