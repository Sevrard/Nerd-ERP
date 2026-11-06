package com.nerd.erp.application.document;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.domain.workspace.SequenceReset;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NumberPatternTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 7);

    @Test
    void defaultPatternReproducesHistoricalNumbering() {
        assertEquals("FAC-2026-00042", NumberPattern.format(null, "FAC", DATE, 42));
        assertEquals("FAC-2026-00042", NumberPattern.format(NumberPattern.DEFAULT, "FAC", DATE, 42));
    }

    @Test
    void formatsEveryToken() {
        assertEquals("DEV/26-03/007", NumberPattern.format("{PREFIX}/{YY}-{MM}/{SEQ:3}", "DEV", DATE, 7));
        assertEquals("2026-000123", NumberPattern.format("{YYYY}-{SEQ:6}", null, DATE, 123));
        assertEquals("F-123456", NumberPattern.format("F-{SEQ:3}", null, DATE, 123456));
    }

    @Test
    void validatesTokensAndPeriodConsistency() {
        assertDoesNotThrow(() -> NumberPattern.validate(null, SequenceReset.YEARLY));
        assertDoesNotThrow(() -> NumberPattern.validate("{PREFIX}-{SEQ}", SequenceReset.NEVER));
        assertDoesNotThrow(() -> NumberPattern.validate("{PREFIX}{YY}{MM}{SEQ:4}", SequenceReset.MONTHLY));
        assertThrows(ApiException.class, () -> NumberPattern.validate("{PREFIX}-{YYYY}", SequenceReset.YEARLY));
        assertThrows(ApiException.class, () -> NumberPattern.validate("{PREFIX}-{SEQ}", SequenceReset.YEARLY));
        assertThrows(ApiException.class, () -> NumberPattern.validate("{PREFIX}-{YYYY}-{SEQ}", SequenceReset.MONTHLY));
        assertThrows(ApiException.class, () -> NumberPattern.validate("{FOO}-{SEQ}", SequenceReset.NEVER));
        assertThrows(ApiException.class, () -> NumberPattern.validate("{SEQ:0}-{YYYY}", SequenceReset.YEARLY));
    }

    @Test
    void periodKeyFollowsReset() {
        assertEquals(2026, SequenceReset.YEARLY.periodKey(DATE));
        assertEquals(202603, SequenceReset.MONTHLY.periodKey(DATE));
        assertEquals(0, SequenceReset.NEVER.periodKey(DATE));
    }
}
