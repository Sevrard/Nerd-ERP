package com.nerd.erp.api.users;

import com.nerd.erp.api.users.TenantUserDtos.InvitationResult;
import com.nerd.erp.api.users.TenantUserDtos.InviteUserRequest;
import com.nerd.erp.api.users.TenantUserDtos.TenantUserResponse;
import com.nerd.erp.api.users.TenantUserDtos.TenantUserUpdateRequest;
import com.nerd.erp.application.users.TenantUserService;
import com.nerd.erp.infrastructure.security.core.RequireAdminAccess;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Utilisateurs du tenant courant (réservé aux administrateurs du tenant). */
@RestController
@RequestMapping("/api/v1/users")
@RequireAdminAccess
public class TenantUserController {

    private final TenantUserService service;

    public TenantUserController(TenantUserService service) {
        this.service = service;
    }

    @GetMapping
    public List<TenantUserResponse> list() {
        return service.list();
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResult invite(@Valid @RequestBody InviteUserRequest request) {
        return service.invite(request);
    }

    @PostMapping("/{userId}/invitations")
    public InvitationResult resend(@PathVariable UUID userId) {
        return service.resendInvitation(userId);
    }

    @PatchMapping("/{userId}")
    public TenantUserResponse update(@PathVariable UUID userId, @Valid @RequestBody TenantUserUpdateRequest request) {
        return service.update(userId, request);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID userId) {
        service.remove(userId);
    }
}
