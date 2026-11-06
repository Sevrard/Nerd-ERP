package com.nerd.erp.application.document;

import com.nerd.erp.domain.document.DocumentSequence;
import com.nerd.erp.domain.document.DocumentSequenceType;
import com.nerd.erp.infrastructure.persistence.document.DocumentSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.UUID;

/**
 * Numérotation séquentielle par workspace / type / année ({@code PREFIX-AAAA-00001}).
 *
 * <p>Concurrence : la ligne de séquence est verrouillée ({@code SELECT … FOR UPDATE}) jusqu'au commit
 * de la transaction appelante — deux utilisateurs qui envoient un devis en même temps obtiennent
 * donc des numéros distincts et consécutifs, et un rollback ne consomme pas de numéro (pas de trou).
 * La création de la ligne elle-même passe par un {@code INSERT … ON CONFLICT DO NOTHING} pour que
 * le tout premier numéro d'un workspace ou d'une année ne provoque pas de collision.
 */
@Service
public class DocumentNumberService {

    private final DocumentSequenceRepository sequenceRepository;

    public DocumentNumberService(DocumentSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    @Transactional
    public String nextNumber(UUID tenantId, UUID workspaceId, DocumentSequenceType type, String prefix) {
        int year = Year.now().getValue();
        sequenceRepository.ensureExists(tenantId, workspaceId, type.name(), year);
        DocumentSequence sequence = sequenceRepository.findForUpdate(workspaceId, type, year)
                .orElseThrow(() -> new IllegalStateException(
                        "Séquence introuvable après création : " + workspaceId + "/" + type + "/" + year));
        long next = sequence.getLastValue() + 1;
        sequence.setLastValue(next);
        sequenceRepository.save(sequence);
        return "%s-%d-%05d".formatted(prefix, year, next);
    }
}
