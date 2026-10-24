package com.company.crm.customer.entity;

import com.company.crm.common.crypto.BlindIndexed;
import com.company.crm.common.crypto.DataEncryption;
import com.company.crm.common.crypto.EncryptedString;
import com.company.crm.common.enums.CustomerStatus;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@EntityListeners(BlindIndexed.Listener.class)
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer implements BlindIndexed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    /** FR-1.6: code from the tenant's configurable industry list (see industries table). */
    @Column(nullable = false, length = 30)
    private String industry;

    @Column(nullable = false)
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Convert(converter = EncryptedString.class)
    private String email;

    @Convert(converter = EncryptedString.class)
    private String phone;

    private String website;

    /** API-G12: this customer's code in the tenant's ERP (set by the ERP integration). */
    @Column(name = "erp_code", length = 64)
    private String erpCode;

    @Convert(converter = EncryptedString.class)
    private String address;

    /** NFR-7.2b: blind indexes for matching inbound email / calls / SMS. */
    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(name = "phone_hash", length = 64)
    private String phoneHash;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** FR-7.1c: unsubscribed from marketing campaigns (set from the unsubscribe link). */
    @Column(name = "marketing_opt_out", nullable = false)
    private boolean marketingOptOut;

    @Column(name = "marketing_opt_out_at")
    private LocalDateTime marketingOptOutAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerContact> contacts = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Override
    public void refreshBlindIndexes(DataEncryption encryption) {
        emailHash = encryption.emailIndex(email);
        phoneHash = encryption.phoneIndex(phone);
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
