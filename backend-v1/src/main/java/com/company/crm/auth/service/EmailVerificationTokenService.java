package com.company.crm.auth.service;

import com.company.crm.auth.entity.EmailVerificationToken;
import com.company.crm.auth.repository.EmailVerificationTokenRepository;
import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.exception.ApiException;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Issues and verifies email-verification tokens. There is no SMTP integration configured
 * anywhere in this project yet, so the token is logged rather than emailed — wiring a real
 * mail sender is a follow-up, not something this class fakes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationTokenService {

    private static final int EXPIRY_HOURS = 48;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public void issueToken(User user) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(LocalDateTime.now().plusHours(EXPIRY_HOURS));
        tokenRepository.save(token);

        log.info("Email verification token for {}: {} (expires in {}h)", user.getEmail(), token.getToken(), EXPIRY_HOURS);
    }

    @Transactional
    public void verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByToken(rawToken)
                .orElseThrow(() -> ApiException.badRequest("Invalid verification token"));

        if (token.getVerifiedAt() != null) {
            throw ApiException.badRequest("This token has already been used");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw ApiException.badRequest("This verification link has expired");
        }

        token.setVerifiedAt(LocalDateTime.now());
        tokenRepository.save(token);

        User user = token.getUser();
        user.setEmailVerified(true);
        user.setStatus(AccountStatus.ACTIVE);
        userRepository.save(user);
    }
}
