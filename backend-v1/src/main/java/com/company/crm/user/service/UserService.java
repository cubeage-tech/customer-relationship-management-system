package com.company.crm.user.service;

import com.company.crm.auth.service.EmailVerificationTokenService;
import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.user.dto.request.CreateUserReqDto;
import com.company.crm.user.dto.response.UserResDto;
import com.company.crm.user.entity.Role;
import com.company.crm.user.entity.User;
import com.company.crm.user.mapper.UserMapper;
import com.company.crm.user.repository.RoleRepository;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    /** Roles an admin may hand out to team members — never admin/super_admin. */
    private static final Set<RoleType> TEAM_ROLES = EnumSet.of(
            RoleType.SALES_MANAGER,
            RoleType.SALES_EXECUTIVE,
            RoleType.MARKETING_EXECUTIVE,
            RoleType.SERVICE_AGENT,
            RoleType.FINANCE_APPROVER,
            RoleType.EXECUTIVE_OWNER
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationTokenService emailVerificationTokenService;

    @Transactional
    public UserResDto createTeamUser(User creator, CreateUserReqDto dto) {
        if (creator.getTenant() == null) {
            throw ApiException.forbidden("Only a tenant admin can add users");
        }

        RoleType requestedRole = parseTeamRole(dto.getRole());

        if (userRepository.existsByEmail(dto.getEmail())) {
            throw ApiException.conflict("A user with this email already exists");
        }

        Role role = roleRepository.findByName(requestedRole)
                .orElseThrow(() -> ApiException.badRequest("Unknown role: " + dto.getRole()));

        User user = new User();
        user.setTenant(creator.getTenant());
        user.setRole(role);
        user.setFullName(dto.getFullName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(AccountStatus.PENDING_VERIFICATION);
        user = userRepository.save(user);

        emailVerificationTokenService.issueToken(user);

        return userMapper.toDto(user);
    }

    public List<UserResDto> listUsers(User currentUser) {
        List<User> users = currentUser.getRole().getName() == RoleType.SUPER_ADMIN
                ? userRepository.findAll()
                : userRepository.findByTenantId(currentUser.getTenant().getId());

        return users.stream().map(userMapper::toDto).toList();
    }

    private RoleType parseTeamRole(String rawRole) {
        RoleType role;
        try {
            role = RoleType.fromDbValue(rawRole);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown role: " + rawRole);
        }
        if (!TEAM_ROLES.contains(role)) {
            throw ApiException.badRequest("Admins may only add team roles, not: " + rawRole);
        }
        return role;
    }
}
