package com.nerd.erp.infrastructure.persistence.customer;

import com.nerd.erp.domain.customer.Customer;
import com.nerd.erp.domain.customer.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenantId = :tenantId AND c.workspaceId = :workspaceId AND c.archived = false
            AND (:status IS NULL OR c.status = :status)
            """)
    Page<Customer> listByWorkspace(@Param("tenantId") UUID tenantId,
                                   @Param("workspaceId") UUID workspaceId,
                                   @Param("status") CustomerStatus status,
                                   Pageable pageable);

    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenantId = :tenantId AND c.workspaceId = :workspaceId AND c.archived = false
            AND (:status IS NULL OR c.status = :status)
            AND (LOWER(COALESCE(c.companyName, '')) LIKE :qPattern
                 OR LOWER(COALESCE(c.firstName, '')) LIKE :qPattern
                 OR LOWER(COALESCE(c.lastName, '')) LIKE :qPattern
                 OR LOWER(COALESCE(c.email, '')) LIKE :qPattern)
            """)
    Page<Customer> searchByWorkspace(@Param("tenantId") UUID tenantId,
                                     @Param("workspaceId") UUID workspaceId,
                                     @Param("status") CustomerStatus status,
                                     @Param("qPattern") String qPattern,
                                     Pageable pageable);

    long countByTenantIdAndWorkspaceIdAndStatusAndArchivedFalse(UUID tenantId, UUID workspaceId, CustomerStatus status);

    Optional<Customer> findByTenantIdAndWorkspaceIdAndArchivedFalseAndEmailIgnoreCase(
            UUID tenantId, UUID workspaceId, String email);

    List<Customer> findByTenantIdAndWorkspaceIdAndArchivedFalseAndCompanyNameIgnoreCase(
            UUID tenantId, UUID workspaceId, String companyName);

    List<Customer> findByTenantIdAndWorkspaceIdAndArchivedFalseAndFirstNameIgnoreCaseAndLastNameIgnoreCase(
            UUID tenantId, UUID workspaceId, String firstName, String lastName);
}
