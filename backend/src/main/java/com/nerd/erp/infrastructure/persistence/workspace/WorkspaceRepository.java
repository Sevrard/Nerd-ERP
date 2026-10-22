package com.nerd.erp.infrastructure.persistence.workspace;

import com.nerd.erp.domain.workspace.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    List<Workspace> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);
    Optional<Workspace> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<Workspace> findByTenantIdAndSlug(UUID tenantId, String slug);
    long countByTenantIdAndActiveTrue(UUID tenantId);
    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);
}
