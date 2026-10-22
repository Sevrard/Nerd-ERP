package com.nerd.erp.api.auth;

import com.nerd.erp.application.auth.AuthService;
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

    public AuthController(AuthService authService, WorkspaceService workspaceService) {
        this.authService = authService;
        this.workspaceService = workspaceService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        AuthenticatedUser user = SecurityContextHelper.requireUser();
        List<WorkspaceResponse> workspaces = workspaceService.listActiveForCurrentTenant();
        return Map.of(
                "userId", user.userId(),
                "tenantId", user.tenantId(),
                "email", user.email(),
                "role", user.role(),
                "workspaces", workspaces);
    }
}
