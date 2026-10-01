package com.company.crm.auth;

import com.company.crm.auth.dto.request.LoginRequest;
import com.company.crm.auth.entity.LoginAttempt;
import com.company.crm.auth.mapper.AuthMapper;
import com.company.crm.auth.repository.LoginAttemptRepository;
import com.company.crm.auth.repository.PasswordResetTokenRepository;
import com.company.crm.auth.service.AuthService;
import com.company.crm.auth.service.EmailVerificationTokenService;
import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.enums.SubscriptionStatus;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.exception.TenantInactiveException;
import com.company.crm.common.security.JwtService;
import com.company.crm.plan.entity.Plan;
import com.company.crm.subscription.service.SubscriptionService;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.tenant.repository.TenantRepository;
import com.company.crm.user.entity.Role;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.RoleRepository;
import com.company.crm.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private LoginAttemptRepository loginAttemptRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthMapper authMapper;
    @Mock private EmailVerificationTokenService emailVerificationTokenService;
    @Mock private SubscriptionService subscriptionService;

    @InjectMocks private AuthService authService;

    @Captor private ArgumentCaptor<Map<String, Object>> claimsCaptor;

    private User user;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(7L);
        tenant.setStatus(AccountStatus.ACTIVE);

        Role role = new Role();
        role.setName(RoleType.ADMIN);

        user = new User();
        user.setId(3L);
        user.setEmail("owner@acme.test");
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setTenant(tenant);
        user.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    void login_wrongPassword_isUnauthorizedAndRecordsFailedAttempt() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest("bad")))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(savedAttempt().getFailureReason()).isEqualTo("invalid_password");
    }

    @Test
    void login_failedAttemptsAreNotRolledBack() throws NoSuchMethodException {
        Transactional tx = AuthService.class.getMethod("login", LoginRequest.class).getAnnotation(Transactional.class);
        assertThat(tx.noRollbackFor()).contains(ApiException.class);
    }

    @Test
    void login_unverifiedUser_isForbidden() {
        user.setStatus(AccountStatus.PENDING_VERIFICATION);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest("pw")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("verify your email");
        verify(jwtService, never()).generateAccessToken(any(), anyMap());
    }

    @Test
    void login_deactivatedTenant_isRejectedWithTenantInactive() {
        tenant.setStatus(AccountStatus.DEACTIVATED);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest("pw")))
                .isInstanceOf(TenantInactiveException.class)
                .extracting("errorCode").isEqualTo("TENANT_INACTIVE");

        assertThat(savedAttempt().getFailureReason()).isEqualTo("tenant_deactivated");
        verify(jwtService, never()).generateAccessToken(any(), anyMap());
    }

    @Test
    void login_success_putsTenantAndSubscriptionClaimsInJwt() {
        Plan plan = new Plan();
        plan.setCode("business");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(subscriptionService.currentPlan(tenant)).thenReturn(plan);
        when(subscriptionService.currentStatus(tenant)).thenReturn(SubscriptionStatus.TRIAL);
        when(jwtService.generateAccessToken(eq(user.getEmail()), anyMap())).thenReturn("jwt");

        authService.login(loginRequest("pw"));

        verify(jwtService).generateAccessToken(eq(user.getEmail()), claimsCaptor.capture());
        assertThat(claimsCaptor.getValue())
                .containsEntry("tenantId", 7L)
                .containsEntry("role", "admin")
                .containsEntry("plan", "business")
                .containsEntry("subscriptionStatus", "trial");
        verify(authMapper).toAuthResponse("jwt", user);
    }

    @Test
    void login_superAdmin_hasNoSubscriptionClaims() {
        user.setTenant(null);
        user.getRole().setName(RoleType.SUPER_ADMIN);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        authService.login(loginRequest("pw"));

        verify(subscriptionService, never()).currentStatus(any());
    }

    private LoginRequest loginRequest(String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword(password);
        return request;
    }

    private LoginAttempt savedAttempt() {
        ArgumentCaptor<LoginAttempt> attempt = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(loginAttemptRepository).save(attempt.capture());
        return attempt.getValue();
    }
}
