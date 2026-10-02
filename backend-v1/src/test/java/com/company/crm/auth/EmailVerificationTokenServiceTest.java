package com.company.crm.auth;

import com.company.crm.auth.entity.EmailVerificationToken;
import com.company.crm.auth.repository.EmailVerificationTokenRepository;
import com.company.crm.auth.service.EmailVerificationTokenService;
import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.InvalidTokenException;
import com.company.crm.common.exception.TokenExpiredException;
import com.company.crm.common.mail.MailService;
import com.company.crm.subscription.service.SubscriptionService;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.Role;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationTokenServiceTest {

    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private MailService mailService;
    @Mock private SubscriptionService subscriptionService;

    @InjectMocks private EmailVerificationTokenService service;

    private User user;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(7L);

        Role admin = new Role();
        admin.setName(RoleType.ADMIN);

        user = new User();
        user.setId(3L);
        user.setEmail("owner@acme.test");
        user.setFullName("Owner");
        user.setRole(admin);
        user.setTenant(tenant);
        user.setStatus(AccountStatus.PENDING_VERIFICATION);
    }

    // ==================== verify ====================

    @Test
    void verify_unknownToken_isInvalid() {
        when(tokenRepository.findByToken("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("nope"))
                .isInstanceOf(InvalidTokenException.class)
                .extracting("errorCode").isEqualTo("TOKEN_INVALID");
    }

    @Test
    void verify_alreadyUsedToken_isInvalid() {
        EmailVerificationToken token = token(LocalDateTime.now().plusHours(1));
        token.setVerifiedAt(LocalDateTime.now().minusMinutes(5));
        when(tokenRepository.findByToken("t")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verify("t"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void verify_expiredToken_isGoneAndUserStaysPending() {
        when(tokenRepository.findByToken("t")).thenReturn(Optional.of(token(LocalDateTime.now().minusMinutes(1))));

        assertThatThrownBy(() -> service.verify("t"))
                .isInstanceOf(TokenExpiredException.class)
                .extracting("errorCode").isEqualTo("TOKEN_EXPIRED");
        assertThat(user.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        verify(userRepository, never()).save(any());
    }

    @Test
    void verify_pendingAdmin_activatesUserAndStartsTrial() {
        EmailVerificationToken token = token(LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByToken("t")).thenReturn(Optional.of(token));

        service.verify("t");

        assertThat(token.getVerifiedAt()).isNotNull();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(subscriptionService).startTrialIfAbsent(tenant);
    }

    @Test
    void verify_teamMember_doesNotStartTrial() {
        user.getRole().setName(RoleType.SALES_EXECUTIVE);
        when(tokenRepository.findByToken("t")).thenReturn(Optional.of(token(LocalDateTime.now().plusHours(1))));

        service.verify("t");

        assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(subscriptionService, never()).startTrialIfAbsent(any());
    }

    @Test
    void verify_suspendedUser_isNotReactivatedByAnOldLink() {
        user.setStatus(AccountStatus.SUSPENDED);
        when(tokenRepository.findByToken("t")).thenReturn(Optional.of(token(LocalDateTime.now().plusHours(1))));

        service.verify("t");

        assertThat(user.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    // ==================== issue / resend ====================

    @Test
    void issueToken_expiresOlderOutstandingTokens_andEmailsTheNewOne() {
        EmailVerificationToken older = token(LocalDateTime.now().plusHours(40));
        when(tokenRepository.findByUserIdAndVerifiedAtIsNullAndExpiresAtAfter(eq(3L), any()))
                .thenReturn(List.of(older));

        service.issueToken(user);

        assertThat(older.getExpiresAt()).isBeforeOrEqualTo(LocalDateTime.now());

        ArgumentCaptor<EmailVerificationToken> saved = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isAfter(LocalDateTime.now().plusHours(47));
        // No transaction in a unit test, so the mail goes out immediately.
        verify(mailService).sendVerificationEmail(user.getEmail(), user.getFullName(), saved.getValue().getToken());
    }

    @Test
    void resend_forVerifiedUser_sendsNothing() {
        user.setEmailVerified(true);
        user.setStatus(AccountStatus.ACTIVE);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        service.resend(user.getEmail());

        verify(tokenRepository, never()).save(any());
        verify(mailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void resend_forUnknownEmail_isSilent() {
        when(userRepository.findByEmail("ghost@x.test")).thenReturn(Optional.empty());

        service.resend("ghost@x.test");

        verify(tokenRepository, never()).save(any());
    }

    @Test
    void resend_forPendingUser_issuesFreshToken() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        service.resend(user.getEmail());

        verify(tokenRepository).save(any(EmailVerificationToken.class));
    }

    private EmailVerificationToken token(LocalDateTime expiresAt) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken("t");
        token.setExpiresAt(expiresAt);
        return token;
    }
}
