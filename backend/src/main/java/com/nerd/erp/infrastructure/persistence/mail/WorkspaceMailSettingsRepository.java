package com.nerd.erp.infrastructure.persistence.mail;

import com.nerd.erp.domain.mail.WorkspaceMailSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMailSettingsRepository extends JpaRepository<WorkspaceMailSettings, UUID> {
    Optional<WorkspaceMailSettings> findByWorkspaceIdAndTenantId(UUID workspaceId, UUID tenantId);
}
