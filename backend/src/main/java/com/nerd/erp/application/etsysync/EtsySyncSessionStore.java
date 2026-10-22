package com.nerd.erp.application.etsysync;

import com.nerd.erp.api.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EtsySyncSessionStore {
    private static final Duration TTL = Duration.ofHours(2);
    private final ConcurrentHashMap<UUID, EtsySyncSession> sessions = new ConcurrentHashMap<>();

    public void save(EtsySyncSession session) {
        purge();
        sessions.put(session.id(), session);
    }

    public EtsySyncSession require(UUID sessionId, UUID tenantId) {
        purge();
        EtsySyncSession session = sessions.get(sessionId);
        if (session == null || session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(sessionId);
            throw new ApiException(HttpStatus.GONE, "Session Etsy expirée");
        }
        if (!session.tenantId().equals(tenantId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Session invalide");
        }
        return session;
    }

    public void remove(UUID sessionId) {
        sessions.remove(sessionId);
    }

    public void removeByWorkspaceId(UUID workspaceId) {
        sessions.entrySet().removeIf(e -> workspaceId.equals(e.getValue().workspaceId()));
    }

    public static Instant defaultExpiry() {
        return Instant.now().plus(TTL);
    }

    private void purge() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }
}
