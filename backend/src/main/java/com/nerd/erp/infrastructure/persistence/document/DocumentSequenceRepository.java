package com.nerd.erp.infrastructure.persistence.document;

import com.nerd.erp.domain.document.DocumentSequence;
import com.nerd.erp.domain.document.DocumentSequenceType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, UUID> {

    /** Verrou pessimiste (SELECT … FOR UPDATE) : sérialise l'attribution des numéros jusqu'au commit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DocumentSequence s WHERE s.workspaceId = :workspaceId AND s.sequenceType = :type AND s.year = :year")
    Optional<DocumentSequence> findForUpdate(@Param("workspaceId") UUID workspaceId,
                                               @Param("type") DocumentSequenceType type,
                                               @Param("year") int year);

    /**
     * Crée la séquence si elle n'existe pas encore, sans échouer si une transaction concurrente
     * l'a créée entre-temps (évite la violation de {@code uq_document_sequence} au premier numéro
     * d'un workspace / d'une année). À appeler avant {@link #findForUpdate}.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO document_sequence (tenant_id, workspace_id, sequence_type, year, last_value)
            VALUES (:tenantId, :workspaceId, :type, :year, 0)
            ON CONFLICT (workspace_id, sequence_type, year) DO NOTHING
            """, nativeQuery = true)
    int ensureExists(@Param("tenantId") UUID tenantId,
                     @Param("workspaceId") UUID workspaceId,
                     @Param("type") String type,
                     @Param("year") int year);
}
