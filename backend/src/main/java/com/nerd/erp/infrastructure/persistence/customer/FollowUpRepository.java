package com.nerd.erp.infrastructure.persistence.customer;

import com.nerd.erp.domain.customer.FollowUp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FollowUpRepository extends JpaRepository<FollowUp, UUID> {

    List<FollowUp> findByTenantIdAndCustomerIdOrderByScheduledAtDesc(UUID tenantId, UUID customerId);

    @Query("""
            SELECT f FROM FollowUp f
            WHERE f.tenantId = :tenantId
            AND (:workspaceId IS NULL OR f.workspaceId = :workspaceId)
            AND f.completed = false AND f.scheduledAt <= :until
            ORDER BY f.scheduledAt ASC
            """)
    List<FollowUp> findUpcoming(@Param("tenantId") UUID tenantId,
                                @Param("workspaceId") UUID workspaceId,
                                @Param("until") Instant until);
}
