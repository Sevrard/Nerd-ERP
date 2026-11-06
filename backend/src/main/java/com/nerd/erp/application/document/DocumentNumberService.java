package com.nerd.erp.application.document;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.domain.document.DocumentSequence;
import com.nerd.erp.domain.document.DocumentSequenceType;
import com.nerd.erp.domain.workspace.SequenceReset;
import com.nerd.erp.domain.workspace.Workspace;
import com.nerd.erp.infrastructure.persistence.document.DocumentSequenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Numérotation séquentielle par workspace / type / période, selon le motif du workspace
 * ({@link NumberPattern}, défaut {@code PREFIX-AAAA-00001}) et sa périodicité de remise à zéro.
 *
 * <p>Concurrence : la ligne de séquence est verrouillée ({@code SELECT … FOR UPDATE}) jusqu'au commit
 * de la transaction appelante — deux utilisateurs qui envoient un devis en même temps obtiennent
 * donc des numéros distincts et consécutifs, et un rollback ne consomme pas de numéro (pas de trou).
 * La création de la ligne elle-même passe par un {@code INSERT … ON CONFLICT DO NOTHING} pour que
 * le tout premier numéro d'un workspace ou d'une période ne provoque pas de collision.
 */
@Service
public class DocumentNumberService {

    public static final String SEQUENCE_BACKWARDS = "SEQUENCE_BACKWARDS";

    private final DocumentSequenceRepository sequenceRepository;

    public DocumentNumberService(DocumentSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    /** État d'un compteur pour la période courante. */
    public record Counter(DocumentSequenceType type, String prefix, int period, long lastValue, String nextNumber) {
    }

    @Transactional
    public String nextNumber(Workspace workspace, DocumentSequenceType type) {
        LocalDate today = LocalDate.now();
        int period = reset(workspace).periodKey(today);
        sequenceRepository.ensureExists(workspace.getTenantId(), workspace.getId(), type.name(), period);
        DocumentSequence sequence = sequenceRepository.findForUpdate(workspace.getId(), type, period)
                .orElseThrow(() -> new IllegalStateException(
                        "Séquence introuvable après création : " + workspace.getId() + "/" + type + "/" + period));
        long next = sequence.getLastValue() + 1;
        sequence.setLastValue(next);
        sequenceRepository.save(sequence);
        return NumberPattern.format(workspace.getNumberPattern(), prefixFor(workspace, type), today, next);
    }

    /** Compteurs de la période courante (sans les créer) avec aperçu du prochain numéro. */
    @Transactional(readOnly = true)
    public List<Counter> counters(Workspace workspace) {
        LocalDate today = LocalDate.now();
        int period = reset(workspace).periodKey(today);
        List<Counter> out = new ArrayList<>();
        for (DocumentSequenceType type : DocumentSequenceType.values()) {
            long last = sequenceRepository.findByWorkspaceIdAndSequenceTypeAndYear(workspace.getId(), type, period)
                    .map(DocumentSequence::getLastValue)
                    .orElse(0L);
            String prefix = prefixFor(workspace, type);
            out.add(new Counter(type, prefix, period, last,
                    NumberPattern.format(workspace.getNumberPattern(), prefix, today, last + 1)));
        }
        return out;
    }

    /**
     * Fixe le prochain numéro de la période courante (reprise d'une numérotation existante à l'onboarding).
     * Un compteur ne peut jamais reculer : on refuse si des numéros supérieurs ont déjà été attribués.
     */
    @Transactional
    public Counter setNextValue(Workspace workspace, DocumentSequenceType type, long nextValue) {
        if (nextValue < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, SEQUENCE_BACKWARDS, "Le prochain numéro doit être ≥ 1.");
        }
        LocalDate today = LocalDate.now();
        int period = reset(workspace).periodKey(today);
        sequenceRepository.ensureExists(workspace.getTenantId(), workspace.getId(), type.name(), period);
        DocumentSequence sequence = sequenceRepository.findForUpdate(workspace.getId(), type, period).orElseThrow();
        if (nextValue - 1 < sequence.getLastValue()) {
            throw new ApiException(HttpStatus.CONFLICT, SEQUENCE_BACKWARDS,
                    "Le compteur est déjà à " + sequence.getLastValue() + " : il ne peut pas reculer.");
        }
        sequence.setLastValue(nextValue - 1);
        sequenceRepository.save(sequence);
        String prefix = prefixFor(workspace, type);
        return new Counter(type, prefix, period, sequence.getLastValue(),
                NumberPattern.format(workspace.getNumberPattern(), prefix, today, nextValue));
    }

    private static SequenceReset reset(Workspace workspace) {
        return workspace.getSequenceReset() == null ? SequenceReset.YEARLY : workspace.getSequenceReset();
    }

    /** Les factures d'acompte partagent la série des factures (continuité chronologique obligatoire). */
    public static String prefixFor(Workspace workspace, DocumentSequenceType type) {
        return switch (type) {
            case QUOTATION -> workspace.getQuotationPrefix();
            case INVOICE -> workspace.getInvoicePrefix();
            case CREDIT_NOTE -> workspace.getCreditNotePrefix();
        };
    }
}
