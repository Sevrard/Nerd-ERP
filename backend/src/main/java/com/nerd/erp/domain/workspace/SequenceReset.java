package com.nerd.erp.domain.workspace;

import java.time.LocalDate;

/** Périodicité de remise à zéro des compteurs de numérotation d'un workspace. */
public enum SequenceReset {
    YEARLY,
    MONTHLY,
    NEVER;

    /** Clé de période stockée dans {@code document_sequence.year} : AAAA, AAAAMM ou 0. */
    public int periodKey(LocalDate date) {
        return switch (this) {
            case YEARLY -> date.getYear();
            case MONTHLY -> date.getYear() * 100 + date.getMonthValue();
            case NEVER -> 0;
        };
    }
}
