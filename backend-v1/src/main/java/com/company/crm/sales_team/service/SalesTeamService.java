package com.company.crm.sales_team.service;

import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.sales_team.dto.request.SalesTeamMembersReqDto;
import com.company.crm.sales_team.dto.request.SalesTeamReqDto;
import com.company.crm.sales_team.dto.responce.SalesTeamResDto;
import com.company.crm.sales_team.entity.SalesTeam;
import com.company.crm.sales_team.mapper.SalesTeamMapper;
import com.company.crm.sales_team.repository.SalesTeamRepository;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Tenant admin's management of sales teams — the basis of the sales_manager TEAM data scope. */
@Service
@RequiredArgsConstructor
public class SalesTeamService {

    private static final String ENTITY = "sales_team";

    /** Roles that may lead a team / belong to one. */
    private static final Set<RoleType> MANAGER_ROLES = EnumSet.of(RoleType.SALES_MANAGER);
    private static final Set<RoleType> MEMBER_ROLES = EnumSet.of(RoleType.SALES_EXECUTIVE);

    private final SalesTeamRepository salesTeamRepository;
    private final UserRepository userRepository;
    private final SalesTeamMapper salesTeamMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<SalesTeamResDto> listTeams(User admin) {
        Long tenantId = requireTenantId(admin);
        Map<Long, List<User>> membersByTeam = userRepository.findByTenantIdAndTeamIdIsNotNull(tenantId).stream()
                .collect(Collectors.groupingBy(User::getTeamId));
        return salesTeamRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .map(team -> salesTeamMapper.toDto(team, membersByTeam.getOrDefault(team.getId(), List.of())))
                .toList();
    }

    @Transactional
    public SalesTeamResDto createTeam(User admin, SalesTeamReqDto dto) {
        Long tenantId = requireTenantId(admin);
        String name = dto.getName().trim();
        if (salesTeamRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            throw ApiException.conflict("A team named \"" + name + "\" already exists");
        }

        SalesTeam team = new SalesTeam();
        team.setTenant(admin.getTenant());
        team.setName(name);
        team.setManager(resolveManager(tenantId, dto.getManagerId()));
        team = salesTeamRepository.save(team);

        auditService.record(admin, AuditAction.TEAM_CREATED, ENTITY, team.getId(),
                "name=" + name + ", managerId=" + dto.getManagerId());
        return salesTeamMapper.toDto(team, List.of());
    }

    @Transactional
    public SalesTeamResDto updateTeam(User admin, Long teamId, SalesTeamReqDto dto) {
        Long tenantId = requireTenantId(admin);
        SalesTeam team = findTeam(tenantId, teamId);
        String name = dto.getName().trim();
        if (salesTeamRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, name, teamId)) {
            throw ApiException.conflict("A team named \"" + name + "\" already exists");
        }

        team.setName(name);
        team.setManager(resolveManager(tenantId, dto.getManagerId()));
        salesTeamRepository.save(team);

        auditService.record(admin, AuditAction.TEAM_UPDATED, ENTITY, teamId,
                "name=" + name + ", managerId=" + dto.getManagerId());
        return salesTeamMapper.toDto(team, userRepository.findByTenantIdAndTeamId(tenantId, teamId));
    }

    /** Replaces the roster. Users listed here leave whatever team they were in before. */
    @Transactional
    public SalesTeamResDto setMembers(User admin, Long teamId, SalesTeamMembersReqDto dto) {
        Long tenantId = requireTenantId(admin);
        SalesTeam team = findTeam(tenantId, teamId);

        Set<Long> requested = new LinkedHashSet<>(dto.getUserIds());
        List<User> members = requested.isEmpty() ? List.of() : userRepository.findByTenantIdAndIdIn(tenantId, requested);
        if (members.size() != requested.size()) {
            throw ApiException.badRequest("Every member must be a user of your company");
        }
        for (User member : members) {
            if (!MEMBER_ROLES.contains(member.getRole().getName())) {
                throw ApiException.badRequest(member.getFullName() + " is not a sales executive and cannot join a sales team");
            }
        }

        userRepository.clearTeam(tenantId, teamId);
        members.forEach(member -> member.setTeamId(teamId));
        userRepository.saveAll(members);

        auditService.record(admin, AuditAction.TEAM_MEMBERS_CHANGED, ENTITY, teamId, "memberIds=" + requested);
        return salesTeamMapper.toDto(team, members);
    }

    @Transactional
    public void deleteTeam(User admin, Long teamId) {
        Long tenantId = requireTenantId(admin);
        SalesTeam team = findTeam(tenantId, teamId);
        userRepository.clearTeam(tenantId, teamId);
        salesTeamRepository.delete(team);
        auditService.record(admin, AuditAction.TEAM_DELETED, ENTITY, teamId, "name=" + team.getName());
    }

    private User resolveManager(Long tenantId, Long managerId) {
        if (managerId == null) {
            return null;
        }
        User manager = userRepository.findByIdAndTenantId(managerId, tenantId)
                .orElseThrow(() -> ApiException.badRequest("Manager must be a user of your company"));
        if (!MANAGER_ROLES.contains(manager.getRole().getName())) {
            throw ApiException.badRequest(manager.getFullName() + " is not a sales manager");
        }
        return manager;
    }

    private SalesTeam findTeam(Long tenantId, Long teamId) {
        return salesTeamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Team not found"));
    }

    private Long requireTenantId(User user) {
        if (user.getTenant() == null) {
            throw ApiException.forbidden("Sales teams are scoped to a tenant");
        }
        return user.getTenant().getId();
    }
}
