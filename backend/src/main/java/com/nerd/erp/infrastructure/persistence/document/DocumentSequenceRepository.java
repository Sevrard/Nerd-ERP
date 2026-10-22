package com.nerd.erp.infrastructure.persistence.document;

import com.nerd.erp.domain.document.DocumentSequence;
import com.nerd.erp.domain.document.DocumentSequenceType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DocumentSequence s WHERE s.workspaceId = :workspaceId AND s.sequenceType = :type AND s.year = :year")
    Optional<DocumentSequence> findForUpdate(@Param("workspaceId") UUID workspaceId,
                                               @Param("type") DocumentSequenceType type,
                                               @Param("year") int year);
}
