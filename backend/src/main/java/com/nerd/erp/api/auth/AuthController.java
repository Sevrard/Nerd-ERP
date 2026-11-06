package com.nerd.erp.api.auth;

import com.nerd.erp.application.auth.AuthService;
import com.nerd.erp.application.module.ModuleAccessService;
import com.nerd.erp.domain.module.ModuleCode;
import com.nerd.erp.infrastructure.security.core.AuthenticatedUser;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import com.nerd.erp.api.workspace.WorkspaceResponse;
import com.nerd.erp.application.workspace.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final WorkspaceService workspaceService;
    private final ModuleAccessService moduleAccessService;

    public AuthController(AuthService authService, WorkspaceService workspaceService,
                          ModuleAccessService moduleAccessService) {
        this.authService = authService;
        this.workspaceService = workspaceService;
        this.moduleAccessService = moduleAccessService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        AuthenticatedUser user = SecurityContextHelper.requireUser();
        List<WorkspaceResponse> workspaces = workspaceService.listActiveForCurrentTenant();
        List<ModuleCode> modules = moduleAccessService.enabledModules(user.tenantId()).stream().sorted().toList();
        return Map.of(
                "userId", user.userId(),
                "tenantId", user.tenantId(),
                "email", user.email(),
                "displayName", authService.displayNameOf(user),
                "role", user.role(),
                "platformAdmin", user.platformAdmin(),
                "modules", modules,
                "workspaces", workspaces);
    }
}
