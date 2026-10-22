package com.nerd.erp.infrastructure.persistence.workspace;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Supprime toutes les données métier rattachées à un workspace (FK ON DELETE RESTRICT sur workspace).
 */
@Component
public class WorkspaceCascadeDeleter {

    @PersistenceContext
    private EntityManager entityManager;

    public void deleteWorkspaceData(UUID tenantId, UUID workspaceId) {
        exec("""
                DELETE FROM payment_allocation pa
                WHERE pa.tenant_id = :tenantId
                  AND (
                    pa.payment_id IN (
                      SELECT p.id FROM payment p
                      WHERE p.tenant_id = :tenantId AND p.workspace_id = :workspaceId
                    )
                    OR pa.invoice_id IN (
                      SELECT i.id FROM invoice i
                      WHERE i.tenant_id = :tenantId AND i.workspace_id = :workspaceId
                    )
                  )
                """, tenantId, workspaceId);

        exec("""
                DELETE FROM payment p
                WHERE p.tenant_id = :tenantId AND p.workspace_id = :workspaceId
                """, tenantId, workspaceId);

        exec("""
                UPDATE quotation q
                SET converted_invoice_id = NULL
                WHERE q.tenant_id = :tenantId AND q.workspace_id = :workspaceId
                """, tenantId, workspaceId);

        exec("""
                UPDATE invoice i
                SET source_quotation_id = NULL
                WHERE i.tenant_id = :tenantId AND i.workspace_id = :workspaceId
                """, tenantId, workspaceId);

        exec("""
                DELETE FROM invoice i
                WHERE i.tenant_id = :tenantId AND i.workspace_id = :workspaceId
                """, tenantId, workspaceId);

        exec("""
                DELETE FROM quotation q
                WHERE q.tenant_id = :tenantId AND q.workspace_id = :workspaceId
                """, tenantId, workspaceId);

        exec("""
                DELETE FROM maintenance_contract m
                WHERE m.tenant_id = :tenantId AND m.workspace_id = :workspaceId
                """, tenantId, workspaceId);

        exec("""
                DELETE FROM follow_up f
                WHERE f.tenant_id = :tenantId AND f.workspace_id = :workspaceId
                """, tenantId, workspaceId);

        exec("""
                DELETE FROM customer c
                WHERE c.tenant_id = :tenantId AND c.workspace_id = :workspaceId
                """, tenantId, workspaceId);

        exec("""
                DELETE FROM product p
                WHERE p.tenant_id = :tenantId AND p.workspace_id = :workspaceId
                """, tenantId, workspaceId);

        exec("""
                DELETE FROM document_sequence ds
                WHERE ds.tenant_id = :tenantId AND ds.workspace_id = :workspaceId
                """, tenantId, workspaceId);

        exec("""
                DELETE FROM merchant m
                WHERE m.tenant_id = :tenantId AND m.workspace_id = :workspaceId
                """, tenantId, workspaceId);
    }

    private void exec(String sql, UUID tenantId, UUID workspaceId) {
        entityManager.createNativeQuery(sql)
                .setParameter("tenantId", tenantId)
                .setParameter("workspaceId", workspaceId)
                .executeUpdate();
    }
}
