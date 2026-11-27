package com.company.crm.user.mapper;

import com.company.crm.user.dto.response.UserResDto;
import com.company.crm.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResDto toDto(User user) {
        return new UserResDto(
                user.getId(),
                user.getTenant() != null ? user.getTenant().getId() : null,
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().getName().getDbValue(),
                user.getStatus().getDbValue(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }
}
