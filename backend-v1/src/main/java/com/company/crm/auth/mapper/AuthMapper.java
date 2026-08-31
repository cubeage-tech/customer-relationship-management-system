package com.company.crm.auth.mapper;

import com.company.crm.auth.dto.response.AuthResponse;
import com.company.crm.auth.dto.response.UserSummaryResDto;
import com.company.crm.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public UserSummaryResDto toSummary(User user) {
        return new UserSummaryResDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().getName().getDbValue(),
                user.getTenant() != null ? user.getTenant().getId() : null
        );
    }

    public AuthResponse toAuthResponse(String token, User user) {
        return new AuthResponse(token, toSummary(user));
    }
}
