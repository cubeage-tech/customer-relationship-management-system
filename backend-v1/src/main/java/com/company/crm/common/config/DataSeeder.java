package com.company.crm.common.config;

import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.RoleType;
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

/**
 * Bootstraps a single platform super_admin account on first startup, since it can never be
 * created through public signup (that only creates tenant admins) and roles seeded via SQL
 * can't carry a password hash. No-op once a super_admin already exists.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.super-admin-email:superadmin@smartcrm.ai}")
    private String superAdminEmail;

    @Value("${app.seed.super-admin-password:ChangeMe123!}")
    private String superAdminPassword;

    @Override
    public void run(String... args) {
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
