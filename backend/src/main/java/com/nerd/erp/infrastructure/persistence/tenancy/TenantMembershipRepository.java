package com.nerd.erp.infrastructure.persistence.tenancy;

import com.nerd.erp.domain.tenancy.TenantMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {
    Optional<TenantMembership> findByTenantIdAndUserId(UUID tenantId, UUID userId);
    List<TenantMembership> findByUserId(UUID userId);
}
