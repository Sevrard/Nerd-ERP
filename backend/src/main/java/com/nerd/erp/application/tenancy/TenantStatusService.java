package com.nerd.erp.application.tenancy;

import com.nerd.erp.domain.tenancy.Tenant;
import com.nerd.erp.infrastructure.persistence.tenancy.TenantRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Statut actif / suspendu d'un tenant, mis en cache brièvement pour pouvoir être vérifié à chaque
 * requête authentifiée sans coût notable. Un tenant suspendu perd l'accès immédiatement, même avec
 * un JWT encore valide.
 */
@Service
public class TenantStatusService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private record Cached(boolean active, Instant expiresAt) {
    }

    private final TenantRepository tenantRepository;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public TenantStatusService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public boolean isActive(UUID tenantId) {
        Cached cached = cache.get(tenantId);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.active();
        }
        boolean active = tenantRepository.findById(tenantId).map(Tenant::isActive).orElse(false);
        cache.put(tenantId, new Cached(active, now.plus(CACHE_TTL)));
        return active;
    }

    public void invalidate(UUID tenantId) {
        cache.remove(tenantId);
    }
}
