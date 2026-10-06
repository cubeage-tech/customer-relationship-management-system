package com.company.crm.support;

import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.security.JwtService;
import com.company.crm.subscription.service.SubscriptionService;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.tenant.repository.TenantRepository;
import com.company.crm.user.entity.Role;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.RoleRepository;
import com.company.crm.user.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Creates isolated tenants with one active, verified user per role, plus their JWTs. */
@Component
public class TestTenants {

    /** A tenant with one user per requested role, keyed by role. */
    public record TestTenant(Tenant tenant, Map<RoleType, User> users, Map<RoleType, String> tokens) {
        public User user(RoleType role) {
            return users.get(role);
        }

        public String token(RoleType role) {
            return tokens.get(role);
        }
    }

    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final JwtService jwtService;

    public TestTenants(TenantRepository tenantRepository, RoleRepository roleRepository, UserRepository userRepository,
                       SubscriptionService subscriptionService, JwtService jwtService) {
        this.tenantRepository = tenantRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.subscriptionService = subscriptionService;
        this.jwtService = jwtService;
    }

    /** A tenant (on an active trial) with one user for every tenant role. */
    @Transactional
    public TestTenant tenantWithAllRoles(String companyName) {
        Tenant tenant = new Tenant();
        tenant.setCompanyName(companyName);
        tenant = tenantRepository.save(tenant);
        subscriptionService.startTrialIfAbsent(tenant);

        Map<RoleType, User> users = new EnumMap<>(RoleType.class);
        Map<RoleType, String> tokens = new EnumMap<>(RoleType.class);
        for (RoleType role : RoleType.values()) {
            if (role == RoleType.SUPER_ADMIN) {
                continue;
            }
            User user = createUser(tenant, role);
            users.put(role, user);
            tokens.put(role, tokenFor(user));
        }
        return new TestTenant(tenant, users, tokens);
    }

    /** A platform super admin (no tenant). */
    @Transactional
    public String superAdminToken() {
        return tokenFor(createUser(null, RoleType.SUPER_ADMIN));
    }

    @Transactional
    public User createUser(Tenant tenant, RoleType roleType) {
        Role role = roleRepository.findByName(roleType).orElseThrow();
        User user = new User();
        user.setTenant(tenant);
        user.setRole(role);
        user.setFullName(roleType.getDbValue() + " user");
        user.setEmail(roleType.getDbValue() + "." + UUID.randomUUID().toString().substring(0, 8) + "@test.local");
        user.setPasswordHash("not-used-tokens-are-minted");
        user.setEmailVerified(true);
        user.setStatus(AccountStatus.ACTIVE);
        return userRepository.save(user);
    }

    public String tokenFor(User user) {
        return jwtService.generateAccessToken(user.getEmail(), Map.of("userId", user.getId()));
    }
}
