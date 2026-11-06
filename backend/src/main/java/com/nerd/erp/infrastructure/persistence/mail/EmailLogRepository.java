package com.nerd.erp.infrastructure.persistence.mail;

import com.nerd.erp.domain.mail.EmailLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmailLogRepository extends JpaRepository<EmailLog, UUID> {
    Page<EmailLog> findByTenantIdAndWorkspaceId(UUID tenantId, UUID workspaceId, Pageable pageable);
}
