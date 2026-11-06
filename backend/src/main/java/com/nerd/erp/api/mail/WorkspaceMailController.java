package com.nerd.erp.api.mail;

import com.nerd.erp.api.mail.MailDtos.EmailLogResponse;
import com.nerd.erp.api.mail.MailDtos.MailTestRequest;
import com.nerd.erp.api.mail.MailDtos.MailTestResponse;
import com.nerd.erp.api.mail.MailDtos.WorkspaceMailSettingsRequest;
import com.nerd.erp.api.mail.MailDtos.WorkspaceMailSettingsResponse;
import com.nerd.erp.api.shared.PageResponse;
import com.nerd.erp.application.mail.WorkspaceMailSettingsService;
import com.nerd.erp.infrastructure.security.core.RequireAdminAccess;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

import java.util.UUID;

/** Messagerie d'un workspace : paramètres SMTP (admin) et journal des envois. */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/mail")
@RequireAdminAccess
public class WorkspaceMailController {

    private final WorkspaceMailSettingsService service;

    public WorkspaceMailController(WorkspaceMailSettingsService service) {
        this.service = service;
    }

    @GetMapping("/settings")
    public WorkspaceMailSettingsResponse get(@PathVariable UUID workspaceId) {
        return service.get(workspaceId);
    }

    @PutMapping("/settings")
    public WorkspaceMailSettingsResponse save(@PathVariable UUID workspaceId,
                                              @Valid @RequestBody WorkspaceMailSettingsRequest request) {
        return service.save(workspaceId, request);
    }

    @DeleteMapping("/settings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID workspaceId) {
        service.delete(workspaceId);
    }

    @PostMapping("/test")
    public MailTestResponse test(@PathVariable UUID workspaceId, @Valid @RequestBody MailTestRequest request) {
        return service.test(workspaceId, request);
    }

    @GetMapping("/log")
    public PageResponse<EmailLogResponse> log(
            @PathVariable UUID workspaceId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(service.log(workspaceId, pageable));
    }
}
