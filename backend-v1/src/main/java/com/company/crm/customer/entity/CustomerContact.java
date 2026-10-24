package com.company.crm.customer.entity;

import com.company.crm.common.crypto.BlindIndexed;
import com.company.crm.common.crypto.DataEncryption;
import com.company.crm.common.crypto.EncryptedString;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@EntityListeners(BlindIndexed.Listener.class)
@Table(name = "customer_contacts")
@Getter
@Setter
@NoArgsConstructor
public class CustomerContact implements BlindIndexed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private String name;

    private String designation;

    @Convert(converter = EncryptedString.class)
    private String phone;

    @Convert(converter = EncryptedString.class)
    private String email;

    /** NFR-7.2b: blind indexes for matching inbound email / calls / SMS. */
    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(name = "phone_hash", length = 64)
    private String phoneHash;

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
