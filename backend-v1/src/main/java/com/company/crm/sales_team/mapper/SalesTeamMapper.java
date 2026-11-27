package com.company.crm.sales_team.mapper;

import com.company.crm.sales_team.dto.responce.SalesTeamResDto;
import com.company.crm.sales_team.entity.SalesTeam;
import com.company.crm.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class SalesTeamMapper {

    public SalesTeamResDto toDto(SalesTeam team, List<User> members) {
        User manager = team.getManager();
        return new SalesTeamResDto(
                team.getId(),
                team.getName(),
                manager != null ? manager.getId() : null,
                manager != null ? manager.getFullName() : null,
                members.stream()
                        .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                        .map(this::toMember)
                        .toList());
    }

    private SalesTeamResDto.Member toMember(User user) {
        return new SalesTeamResDto.Member(user.getId(), user.getFullName(), user.getEmail(),
                user.getRole().getName().getDbValue());
    }
}
