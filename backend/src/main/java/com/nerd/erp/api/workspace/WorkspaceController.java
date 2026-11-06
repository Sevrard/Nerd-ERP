package com.nerd.erp.api.workspace;

import com.nerd.erp.application.workspace.WorkspaceService;
import com.nerd.erp.infrastructure.security.core.RequireAdminAccess;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public List<WorkspaceResponse> list() {
        return workspaceService.listActiveForCurrentTenant();
    }

    @GetMapping("/{id}")
    public WorkspaceResponse get(@PathVariable UUID id) {
        return workspaceService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireAdminAccess
    public WorkspaceResponse create(@Valid @RequestBody WorkspaceCreateRequest request) {
        return workspaceService.create(request);
    }

    @PutMapping("/{id}")
    @RequireAdminAccess
    public WorkspaceResponse update(@PathVariable UUID id, @Valid @RequestBody WorkspaceUpdateRequest request) {
        return workspaceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireAdminAccess
    public void delete(@PathVariable UUID id) {
        workspaceService.delete(id);
    }
}
