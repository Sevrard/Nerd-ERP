package com.nerd.erp.infrastructure.persistence.tenancy;

import com.nerd.erp.domain.tenancy.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findBySlug(String slug);
    boolean existsBySlug(String slug);

    @Query("""
            SELECT t FROM Tenant t
            WHERE (:qPattern IS NULL OR LOWER(t.name) LIKE :qPattern OR LOWER(t.slug) LIKE :qPattern)
            """)
    Page<Tenant> search(@Param("qPattern") String qPattern, Pageable pageable);
}
