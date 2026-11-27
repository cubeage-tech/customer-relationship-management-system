package com.company.crm.auth.repository;

import com.company.crm.auth.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByToken(String token);

    /** Still-usable tokens for a user — invalidated whenever a fresh one is issued. */
    List<EmailVerificationToken> findByUserIdAndVerifiedAtIsNullAndExpiresAtAfter(Long userId, LocalDateTime now);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);
}
