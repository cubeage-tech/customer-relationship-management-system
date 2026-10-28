package com.company.crm.tenant.entity;

import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.SubscriptionPlan;
import com.company.crm.common.crypto.EncryptedString;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "legal_name")
    private String legalName;

    /** NFR-7.2b: a payment detail, so stored encrypted. */
    @Column(name = "bank_account_number")
    @Convert(converter = EncryptedString.class)
    private String bankAccountNumber;

    // AC-4.2: branding printed on quotation PDFs (all optional).
    @Column(name = "brand_address", length = 300)
    private String brandAddress;

    @Column(name = "brand_phone", length = 30)
    private String brandPhone;

    @Column(name = "brand_email", length = 150)
    private String brandEmail;

    /** GSTIN or other tax registration number. */
    @Column(name = "brand_tax_id", length = 30)
    private String brandTaxId;

    /** "#rrggbb", used for the company name and rules on the PDF. */
    @Column(name = "brand_color", length = 7)
    private String brandColor;

    /** Document-storage key of the logo (PNG/JPEG). */
    @Column(name = "logo_key", length = 200)
    private String logoKey;

    @Column(name = "logo_content_type", length = 30)
    private String logoContentType;

    /** FR-6.1: secret of the public "Report an issue" form; null until the admin enables it. */
    @Column(name = "support_form_token", length = 64)
    private String supportFormToken;

    @Column(nullable = false)
    private SubscriptionPlan plan = SubscriptionPlan.STARTER;

    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    /** SEC-6: the tenant can switch AI features off entirely. */
    @Column(name = "ai_enabled", nullable = false)
    private boolean aiEnabled = true;

    /** SEC-5: whether message text (emails, WhatsApp, SMS) may be sent to the AI provider (sentiment, AI alerts). */
    @Column(name = "ai_message_content", nullable = false)
    private boolean aiMessageContent = true;

    /** FR-AI-6.1: classify the sentiment of customer messages and feedback as they arrive (sends their text). */
    @Column(name = "ai_sentiment_auto", nullable = false)
    private boolean aiSentimentAuto;

    /** BIZ-2: the industry edition (standard, manufacturing, real_estate, healthcare, industrial_sales). */
    @Column(nullable = false, length = 30)
    private String edition = "standard";

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
