package com.nerd.erp.infrastructure.persistence.audit;

import com.nerd.erp.domain.audit.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
}
