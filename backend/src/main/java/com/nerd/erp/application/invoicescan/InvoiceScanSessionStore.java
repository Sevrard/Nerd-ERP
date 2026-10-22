package com.nerd.erp.application.invoicescan;

import com.nerd.erp.api.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InvoiceScanSessionStore {

    private static final Duration TTL = Duration.ofMinutes(30);

    private final ConcurrentHashMap<UUID, InvoiceScanSession> sessions = new ConcurrentHashMap<>();

    public InvoiceScanSession create(UUID tenantId, UUID workspaceId, UUID userId,
                                     ExtractedInvoiceData extraction,
                                     CustomerMatchStatus customerMatchStatus,
                                     UUID matchedCustomerId,
                                     byte[] sourcePagePdf) {
        purgeExpired();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        InvoiceScanSession session = new InvoiceScanSession(
                id, tenantId, workspaceId, userId, extraction,
                customerMatchStatus, matchedCustomerId, sourcePagePdf, now, now.plus(TTL));
        sessions.put(id, session);
        return session;
    }

    public InvoiceScanSession require(UUID scanId, UUID tenantId) {
        purgeExpired();
        InvoiceScanSession session = sessions.get(scanId);
        if (session == null || session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(scanId);
            throw new ApiException(HttpStatus.GONE, "Session d'analyse expirée, relancez le scan");
        }
        if (!session.tenantId().equals(tenantId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Session invalide");
        }
        return session;
    }

    public void remove(UUID scanId) {
        sessions.remove(scanId);
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    public Optional<InvoiceScanSession> find(UUID scanId) {
        purgeExpired();
        return Optional.ofNullable(sessions.get(scanId));
    }
}
