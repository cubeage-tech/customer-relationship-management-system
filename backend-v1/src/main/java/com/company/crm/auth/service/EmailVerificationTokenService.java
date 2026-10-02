package com.company.crm.auth.service;

import com.company.crm.auth.entity.EmailVerificationToken;
import com.company.crm.auth.repository.EmailVerificationTokenRepository;
import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.InvalidTokenException;
import com.company.crm.common.exception.TokenExpiredException;
import com.company.crm.common.mail.MailService;
import com.company.crm.subscription.service.SubscriptionService;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;

/** Issues and verifies email-verification tokens, and emails the verification link. */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationTokenService {

    private static final int EXPIRY_HOURS = 48;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final SubscriptionService subscriptionService;

    /** Issues a fresh token (invalidating any older unused ones) and emails the link once the transaction commits. */
    @Transactional
    public void issueToken(User user) {
        LocalDateTime now = LocalDateTime.now();

        // Only the newest link should work — expire any earlier ones still outstanding.
        tokenRepository.findByUserIdAndVerifiedAtIsNullAndExpiresAtAfter(user.getId(), now)
                .forEach(old -> old.setExpiresAt(now));

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(now.plusHours(EXPIRY_HOURS));
        tokenRepository.save(token);

        log.info("Email verification token for {}: {} (expires in {}h)", user.getEmail(), token.getToken(), EXPIRY_HOURS);
        sendAfterCommit(user.getEmail(), user.getFullName(), token.getToken());
    }

    /** Re-sends the link to a still-unverified user. Silent otherwise, so it can't be used to probe emails. */
    @Transactional
    public void resend(String email) {
        userRepository.findByEmail(email)
                .filter(user -> !user.isEmailVerified() && user.getStatus() == AccountStatus.PENDING_VERIFICATION)
                .ifPresent(this::issueToken);
    }

    @Transactional
    public void verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new InvalidTokenException("Invalid verification token"));

        if (token.getVerifiedAt() != null) {
            throw new InvalidTokenException("This token has already been used");
        }
        if (!token.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new TokenExpiredException("This verification link has expired. Please request a new one.");
        }

        token.setVerifiedAt(LocalDateTime.now());
        tokenRepository.save(token);

        User user = token.getUser();
        user.setEmailVerified(true);
        // Only a pending account is activated — a suspended/deactivated user can't use an
        // old link to switch themselves back on.
        if (user.getStatus() == AccountStatus.PENDING_VERIFICATION) {
            user.setStatus(AccountStatus.ACTIVE);
        }
        userRepository.save(user);

        // The tenant owner confirming their email is what starts the free trial.
        if (user.getRole().getName() == RoleType.ADMIN && user.getTenant() != null) {
            subscriptionService.startTrialIfAbsent(user.getTenant());
        }
    }

    private void sendAfterCommit(String email, String fullName, String rawToken) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            mailService.sendVerificationEmail(email, fullName, rawToken);
            return;
        }
        // Don't email a link for a token that might still be rolled back.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mailService.sendVerificationEmail(email, fullName, rawToken);
            }
        });
    }
}
