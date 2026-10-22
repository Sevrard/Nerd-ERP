package com.nerd.erp.application.audit;

import com.nerd.erp.domain.audit.AuditEvent;
import com.nerd.erp.infrastructure.persistence.audit.AuditEventRepository;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public void record(UUID tenantId, String entityType, UUID entityId, String action, Map<String, Object> details) {
        AuditEvent event = new AuditEvent();
        event.setTenantId(tenantId);
        try {
            event.setUserId(SecurityContextHelper.requireUser().userId());
        } catch (Exception ignored) {
            // bootstrap / system
        }
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setAction(action);
        event.setDetails(details);
        auditEventRepository.save(event);
    }
}
