package com.nerd.erp.infrastructure.persistence.module;

import com.nerd.erp.domain.module.ModuleCode;
import com.nerd.erp.domain.module.TenantModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantModuleRepository extends JpaRepository<TenantModule, UUID> {

    List<TenantModule> findByTenantId(UUID tenantId);

    Optional<TenantModule> findByTenantIdAndModuleCode(UUID tenantId, ModuleCode moduleCode);

    List<TenantModule> findByTenantIdIn(List<UUID> tenantIds);
}
