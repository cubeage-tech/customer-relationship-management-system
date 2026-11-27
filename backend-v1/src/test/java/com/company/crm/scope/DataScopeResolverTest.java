package com.company.crm.scope;

import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.scope.DataScope;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.sales_team.repository.SalesTeamRepository;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.Role;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataScopeResolverTest {

    @Mock private SalesTeamRepository salesTeamRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private DataScopeResolver resolver;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(resolver, "salesTeamScopeEnabled", true);
        tenant = new Tenant();
        tenant.setId(7L);
    }

    @ParameterizedTest
    @EnumSource(value = RoleType.class, names = {"ADMIN", "MARKETING_EXECUTIVE", "FINANCE_APPROVER", "EXECUTIVE_OWNER", "SERVICE_AGENT"})
    void nonSalesRolesSeeTheWholeTenant(RoleType role) {
        DataScope scope = resolver.resolve(user(1L, role));

        assertThat(scope.isTenantWide()).isTrue();
        assertThat(scope.allows(null)).isTrue();
        verifyNoInteractions(salesTeamRepository, userRepository);
    }

    @Test
    void salesExecutiveSeesOnlyOwnRecords() {
        DataScope scope = resolver.resolve(user(5L, RoleType.SALES_EXECUTIVE));

        assertThat(scope.level()).isEqualTo(DataScope.Level.OWN);
        assertThat(scope.allows(5L)).isTrue();
        assertThat(scope.allows(6L)).isFalse();
        assertThat(scope.allows(null)).isFalse(); // unassigned records are not "theirs"
    }

    @Test
    void salesManagerSeesTeamMembersAndThemself() {
        when(salesTeamRepository.findIdsManagedBy(7L, 2L)).thenReturn(List.of(10L, 11L));
        when(userRepository.findIdsByTenantIdAndTeamIdIn(7L, List.of(10L, 11L))).thenReturn(List.of(5L, 6L));

        DataScope scope = resolver.resolve(user(2L, RoleType.SALES_MANAGER));

        assertThat(scope.level()).isEqualTo(DataScope.Level.TEAM);
        assertThat(scope.ownerIds()).containsExactlyInAnyOrder(2L, 5L, 6L);
        assertThat(scope.allows(9L)).isFalse();
        assertThat(scope.cacheKey()).isEqualTo("TEAM:2,5,6");
    }

    @Test
    void salesManagerWithoutTeamFallsBackToTenantWide() {
        when(salesTeamRepository.findIdsManagedBy(7L, 2L)).thenReturn(List.of());

        assertThat(resolver.resolve(user(2L, RoleType.SALES_MANAGER)).isTenantWide()).isTrue();
    }

    @Test
    void featureFlagOffRestoresTenantWideForManagers() {
        ReflectionTestUtils.setField(resolver, "salesTeamScopeEnabled", false);

        assertThat(resolver.resolve(user(2L, RoleType.SALES_MANAGER)).isTenantWide()).isTrue();
        verifyNoInteractions(salesTeamRepository);
    }

    @Test
    void assertCanAccess_forbidsRecordsOutsideScope() {
        User executive = user(5L, RoleType.SALES_EXECUTIVE);

        assertThatCode(() -> resolver.assertCanAccess(executive, 5L, "lead")).doesNotThrowAnyException();
        assertThatThrownBy(() -> resolver.assertCanAccess(executive, 6L, "lead"))
                .isInstanceOf(ApiException.class)
                .hasMessage("You do not have access to this lead")
                .extracting("status").isEqualTo(HttpStatus.FORBIDDEN);
    }

    private User user(Long id, RoleType roleType) {
        Role role = new Role();
        role.setName(roleType);
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setTenant(tenant);
        return user;
    }
}
