package com.nerd.erp.infrastructure.persistence.organization;

import com.nerd.erp.domain.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    List<Organization> findByTenantIdOrderByLegalNameAsc(UUID tenantId);
}
