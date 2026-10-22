package com.nerd.erp.infrastructure.persistence.merchant;

import com.nerd.erp.domain.merchant.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    List<Merchant> findByTenantIdAndWorkspaceIdOrderByNameAsc(UUID tenantId, UUID workspaceId);

    Optional<Merchant> findByTenantIdAndWorkspaceIdAndSlug(UUID tenantId, UUID workspaceId, String slug);

    boolean existsByTenantIdAndWorkspaceIdAndSlug(UUID tenantId, UUID workspaceId, String slug);

    java.util.Optional<Merchant> findByIdAndTenantId(UUID id, UUID tenantId);
}
