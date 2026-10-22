package com.nerd.erp.infrastructure.persistence.maintenance;

import com.nerd.erp.domain.maintenance.MaintenanceContract;
import com.nerd.erp.domain.maintenance.MaintenanceContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceContractRepository extends JpaRepository<MaintenanceContract, UUID> {

    Optional<MaintenanceContract> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<MaintenanceContract> findByTenantIdAndWorkspaceIdOrderByNextRenewalDateAsc(UUID tenantId, UUID workspaceId,
                                                                                    Pageable pageable);

    @Query("""
            SELECT COUNT(m) FROM MaintenanceContract m
            WHERE m.tenantId = :tenantId
            AND (:workspaceId IS NULL OR m.workspaceId = :workspaceId)
            AND m.status IN ('ACTIVE', 'RENEWAL_DUE')
            AND m.nextRenewalDate IS NOT NULL AND m.nextRenewalDate <= :until
            """)
    long countRenewalsDue(@Param("tenantId") UUID tenantId,
                          @Param("workspaceId") UUID workspaceId,
                          @Param("until") LocalDate until);

    boolean existsByTenantIdAndProductId(UUID tenantId, UUID productId);

    boolean existsByTenantIdAndCustomerId(UUID tenantId, UUID customerId);
}
