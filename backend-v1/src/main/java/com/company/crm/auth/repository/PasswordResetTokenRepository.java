package com.company.crm.auth.repository;

import com.company.crm.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);

    List<PasswordResetToken> findByUserIdAndUsedAtIsNullAndExpiresAtAfter(Long userId, LocalDateTime now);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);
}
