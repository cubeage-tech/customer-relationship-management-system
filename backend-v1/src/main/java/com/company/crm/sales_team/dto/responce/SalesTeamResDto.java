package com.company.crm.sales_team.dto.responce;

import java.util.List;

public record SalesTeamResDto(
        Long id,
        String name,
        Long managerId,
        String managerName,
        List<Member> members
) {
    public record Member(Long id, String fullName, String email, String role) {}
}
