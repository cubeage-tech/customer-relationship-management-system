package com.company.crm.lead.entity;

import com.company.crm.common.crypto.BlindIndexed;
import com.company.crm.common.crypto.DataEncryption;
import com.company.crm.common.crypto.EncryptedString;
import com.company.crm.campaign.entity.Campaign;
import com.company.crm.sales_team.entity.SalesTeam;
import com.company.crm.common.enums.IndustryType;
import com.company.crm.common.enums.LeadSource;
import com.company.crm.common.enums.LeadStage;
import com.company.crm.customer.entity.Customer;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@EntityListeners(BlindIndexed.Listener.class)
@Table(name = "leads")
@Getter
@Setter
@NoArgsConstructor
public class Lead implements BlindIndexed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "lead_name", nullable = false)
    private String leadName;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "contact_email")
    @Convert(converter = EncryptedString.class)
    private String contactEmail;

    @Column(name = "contact_phone")
    @Convert(converter = EncryptedString.class)
    private String contactPhone;

    /** NFR-7.2b: blind indexes for matching inbound email / calls / SMS. */
    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(name = "phone_hash", length = 64)
    private String phoneHash;

    @Override
    public void refreshBlindIndexes(DataEncryption encryption) {
        emailHash = encryption.emailIndex(contactEmail);
        phoneHash = encryption.phoneIndex(contactPhone);
    }

    @Column(nullable = false)
    private IndustryType industry;

    @Column(nullable = false)
    private LeadSource source;

    @Column(nullable = false)
    private LeadStage stage = LeadStage.NEW_LEAD;

    @Column(name = "follow_up_date")
    private LocalDateTime followUpDate;

    /** FR-2.5: when the owner was reminded about the current follow-up date; cleared when the date changes. */
    @Column(name = "follow_up_notified_at")
    private LocalDateTime followUpNotifiedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    /** FR-2.3: the sales team this lead is assigned to (optional, alongside or before an owner). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private SalesTeam team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_customer_id")
    private Customer convertedCustomer;

    /** FR-5.3: the marketing campaign that generated this lead, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    /** SEC-6: never send this lead's data to the AI provider. */
    @Column(name = "ai_excluded", nullable = false)
    private boolean aiExcluded;

    /** SEC-8: when the lead's personal data was erased on request. */
    @Column(name = "erased_at")
    private LocalDateTime erasedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
