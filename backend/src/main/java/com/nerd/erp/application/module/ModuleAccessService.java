package com.nerd.erp.application.module;

import com.nerd.erp.domain.module.ModuleCode;
import com.nerd.erp.domain.module.TenantModule;
import com.nerd.erp.infrastructure.persistence.module.TenantModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modules actifs d'un tenant. Lecture mise en cache quelques secondes (une vérification par requête
 * sur les endpoints de module) ; le cache est invalidé à chaque modification via {@link #invalidate(UUID)}.
 */
@Service
public class ModuleAccessService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private record CachedModules(Set<ModuleCode> modules, Instant expiresAt) {
    }

    private final TenantModuleRepository tenantModuleRepository;
    private final Map<UUID, CachedModules> cache = new ConcurrentHashMap<>();

    public ModuleAccessService(TenantModuleRepository tenantModuleRepository) {
        this.tenantModuleRepository = tenantModuleRepository;
    }

    @Transactional(readOnly = true)
    public Set<ModuleCode> enabledModules(UUID tenantId) {
        CachedModules cached = cache.get(tenantId);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.modules();
        }
        Set<ModuleCode> modules = load(tenantId);
        cache.put(tenantId, new CachedModules(modules, now.plus(CACHE_TTL)));
        return modules;
    }

    public boolean isEnabled(UUID tenantId, ModuleCode code) {
        return enabledModules(tenantId).contains(code);
    }

    public void invalidate(UUID tenantId) {
        cache.remove(tenantId);
    }

    private Set<ModuleCode> load(UUID tenantId) {
        LocalDate today = LocalDate.now();
        Set<ModuleCode> enabled = EnumSet.noneOf(ModuleCode.class);
        for (TenantModule tm : tenantModuleRepository.findByTenantId(tenantId)) {
            if (tm.isCurrentlyActive(today)) {
                enabled.add(tm.getModuleCode());
            }
        }
        return Set.copyOf(enabled);
    }
}
