package com.company.crm.auth.mfa;

import com.company.crm.auth.mfa.MfaService.Setup;
import com.company.crm.auth.mfa.MfaService.Status;
import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** SEC-1c: a signed-in user's own two-step sign-in, and the admin reset for a lost phone. */
@RestController
@RequestMapping("/api/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;

    public record CodeReq(@NotBlank @Size(max = 20) String code) {}

    public record RecoveryCodes(List<String> recoveryCodes) {}

    @GetMapping
    @PreAuthorize(RoleExpressions.ANY_AUTHENTICATED)
    @Operation(summary = "SEC-1c: whether my two-step sign-in is on, and whether my role requires it")
    public Response<Status> status(@AuthenticationPrincipal User currentUser) {
        return Response.ok(mfaService.status(currentUser));
    }

    @PostMapping("/setup")
    @PreAuthorize(RoleExpressions.ANY_AUTHENTICATED)
    @Operation(summary = "SEC-1c: a new secret for my authenticator app (confirm it with /enable)")
    public Response<Setup> setup(@AuthenticationPrincipal User currentUser) {
        return Response.ok(mfaService.beginSetupForSelf(currentUser));
    }

    @PostMapping("/enable")
    @PreAuthorize(RoleExpressions.ANY_AUTHENTICATED)
    @Operation(summary = "SEC-1c: turn two-step sign-in on with a code from the app; returns recovery codes (shown once)")
    public Response<RecoveryCodes> enable(@AuthenticationPrincipal User currentUser, @Valid @RequestBody CodeReq req) {
        return Response.ok("Two-step sign-in is on", new RecoveryCodes(mfaService.confirmSetupForSelf(currentUser, req.code())));
    }

    @PostMapping("/disable")
    @PreAuthorize(RoleExpressions.ANY_AUTHENTICATED)
    @Operation(summary = "SEC-1c: turn two-step sign-in off (not for roles that require it)")
    public Response<Void> disable(@AuthenticationPrincipal User currentUser, @Valid @RequestBody CodeReq req) {
        mfaService.disable(currentUser, req.code());
        return Response.ok("Two-step sign-in is off", null);
    }

    @PostMapping("/recovery-codes")
    @PreAuthorize(RoleExpressions.ANY_AUTHENTICATED)
    @Operation(summary = "SEC-1c: new recovery codes (the old ones stop working)")
    public Response<RecoveryCodes> recoveryCodes(@AuthenticationPrincipal User currentUser, @Valid @RequestBody CodeReq req) {
        return Response.ok(new RecoveryCodes(mfaService.regenerateRecoveryCodes(currentUser, req.code())));
    }

    @PostMapping("/users/{userId}/reset")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "SEC-1c: reset a user's two-step sign-in (lost phone) — tenant admin for their users, platform admin for anyone")
    public Response<Void> reset(@AuthenticationPrincipal User currentUser, @PathVariable Long userId) {
        mfaService.reset(currentUser, userId);
        return Response.ok("Two-step sign-in reset", null);
    }
}
