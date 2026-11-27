package com.company.crm.rolepermission;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.rolepermission.RolePermissionService.RolePermissions;
import com.company.crm.rolepermission.RolePermissionService.UpdateReq;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** ROLE-8: the tenant admin customises which of each role's permissions are on. */
@RestController
@RequestMapping("/api/role-permissions")
@RequiredArgsConstructor
public class RolePermissionController {

    private final RolePermissionService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ROLE-8: every role's default permissions and which are switched off in this tenant")
    public Response<List<RolePermissions>> list(@AuthenticationPrincipal User currentUser) {
        return Response.ok(service.list(currentUser));
    }

    @GetMapping("/mine")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "ROLE-8: the caller's role permissions as customised by the tenant")
    public Response<RolePermissions> mine(@AuthenticationPrincipal User currentUser) {
        return Response.ok(service.mine(currentUser));
    }

    @PutMapping("/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ROLE-8: set which of a role's default permissions are switched off (not the admin role)")
    public Response<RolePermissions> update(@AuthenticationPrincipal User currentUser, @PathVariable String role,
                                            @RequestBody UpdateReq req) {
        return Response.ok("Permissions updated", service.update(currentUser, role, req));
    }

    /** Applies the tenant's switches to every API call. */
    @Configuration
    @RequiredArgsConstructor
    static class GuardConfig implements WebMvcConfigurer {

        private final PermissionGuard guard;

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(guard).addPathPatterns("/api/**").excludePathPatterns("/api/role-permissions/**");
        }
    }
}
