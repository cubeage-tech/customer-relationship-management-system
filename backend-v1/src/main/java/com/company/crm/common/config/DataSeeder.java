package com.company.crm.common.config;

import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.plan.repository.PlanRepository;
import com.company.crm.user.entity.Role;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.RoleRepository;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Startup bootstrap, safe to run on every boot.
 * <ol>
 *   <li>Reference data — roles (V3) and plans (V14) — is seeded by Flyway before the app
 *       starts; this only verifies it's there so a broken migration fails fast.</li>
 *   <li>Creates the single platform super_admin, since it can never be created through public
 *       signup and SQL can't compute its bcrypt hash. No-op once it exists.</li>
 * </ol>
 * No default tenant is seeded: tenants are created by public signup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private static final List<String> REQUIRED_PLANS = List.of("starter", "business", "enterprise");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PlanRepository planRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.super-admin-email:superadmin@smartcrm.ai}")
    private String superAdminEmail;

    @Value("${app.seed.super-admin-password:ChangeMe123!}")
    private String superAdminPassword;

    @Override
    public void run(String... args) {
        verifyPlansSeeded();
        seedSuperAdmin();
    }

    private void verifyPlansSeeded() {
        for (String code : REQUIRED_PLANS) {
            if (planRepository.findByCode(code).isEmpty()) {
                throw new IllegalStateException("Plan '" + code + "' is not seeded — check V14__add_plans_subscriptions_payments.sql");
            }
        }
    }

    private void seedSuperAdmin() {
        boolean superAdminExists = userRepository.findByEmail(superAdminEmail).isPresent();
        if (superAdminExists) {
            return;
        }

        Role superAdminRole = roleRepository.findByName(RoleType.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("super_admin role is not seeded — check V3__seed_data.sql"));

        User user = new User();
        user.setRole(superAdminRole);
        user.setTenant(null);
        user.setFullName("Platform Super Admin");
        user.setEmail(superAdminEmail);
        user.setPasswordHash(passwordEncoder.encode(superAdminPassword));
        user.setEmailVerified(true);
        user.setStatus(AccountStatus.ACTIVE);
        userRepository.save(user);

        log.warn("Seeded bootstrap super_admin account: {} — change its password after first login.", superAdminEmail);
    }
}
