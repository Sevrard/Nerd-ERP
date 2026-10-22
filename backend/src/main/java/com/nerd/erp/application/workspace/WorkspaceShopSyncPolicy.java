package com.nerd.erp.application.workspace;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.domain.workspace.Workspace;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Optional;

/**
 * Workspaces dont les factures proviennent d'une sync boutique (pas d'import PDF manuel).
 */
public final class WorkspaceShopSyncPolicy {

    private static final Map<String, String> PROVIDER_BY_SLUG = Map.of(
            "nerd-forge", "Etsy"
    );

    private WorkspaceShopSyncPolicy() {
    }

    public static boolean hasShopSync(Workspace workspace) {
        return workspace != null && PROVIDER_BY_SLUG.containsKey(workspace.getSlug());
    }

    public static Optional<String> shopSyncProviderLabel(Workspace workspace) {
        if (workspace == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(PROVIDER_BY_SLUG.get(workspace.getSlug()));
    }

    public static void ensurePdfImportAllowed(Workspace workspace) {
        shopSyncProviderLabel(workspace).ifPresent(provider -> {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Import PDF indisponible : ce workspace utilise la synchronisation " + provider + ".");
        });
    }
}
