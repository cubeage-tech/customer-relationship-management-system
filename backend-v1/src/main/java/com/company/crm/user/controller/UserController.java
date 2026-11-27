package com.company.crm.user.controller;

import com.company.crm.common.response.Response;
import com.company.crm.user.dto.request.CreateUserReqDto;
import com.company.crm.user.dto.response.UserResDto;
import com.company.crm.user.entity.User;
import com.company.crm.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.company.crm.common.security.RoleExpressions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // Open to every signed-in role on purpose: owner/technician pickers on detail pages need it.
    // UserService scopes it — tenant users get their own tenant only.
    @GetMapping
    @PreAuthorize(RoleExpressions.ANY_AUTHENTICATED)
    public Response<List<UserResDto>> listUsers(@AuthenticationPrincipal User currentUser) {
        return Response.ok(userService.listUsers(currentUser));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Response<UserResDto> createUser(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateUserReqDto dto) {
        return Response.ok("User created", userService.createTeamUser(currentUser, dto));
    }
}
