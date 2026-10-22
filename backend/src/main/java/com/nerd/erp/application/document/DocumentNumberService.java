package com.nerd.erp.application.document;

import com.nerd.erp.domain.document.DocumentSequence;
import com.nerd.erp.domain.document.DocumentSequenceType;
import com.nerd.erp.infrastructure.persistence.document.DocumentSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.UUID;

@Service
public class DocumentNumberService {

    private final DocumentSequenceRepository sequenceRepository;

    public DocumentNumberService(DocumentSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    @Transactional
    public String nextNumber(UUID tenantId, UUID workspaceId, DocumentSequenceType type, String prefix) {
        int year = Year.now().getValue();
        DocumentSequence sequence = sequenceRepository.findForUpdate(workspaceId, type, year)
                .orElseGet(() -> {
                    DocumentSequence created = new DocumentSequence();
                    created.setTenantId(tenantId);
                    created.setWorkspaceId(workspaceId);
                    created.setSequenceType(type);
                    created.setYear(year);
                    created.setLastValue(0);
                    return sequenceRepository.save(created);
                });
        long next = sequence.getLastValue() + 1;
        sequence.setLastValue(next);
        sequenceRepository.save(sequence);
        return "%s-%d-%05d".formatted(prefix, year, next);
    }
}
