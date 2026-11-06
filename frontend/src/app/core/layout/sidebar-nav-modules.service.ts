import { Injectable, computed, inject, signal } from '@angular/core';
import { ModuleService } from '../modules/module.service';
import { ModuleCode } from '../../shared/models/auth.model';

const STORAGE_KEY = 'nerd-erp-nav-modules-visible';

export interface SidebarNavItem {
  path: string;
  label: string;
  /** Rubrique visible uniquement si ce module optionnel est activé pour le tenant. */
  module?: ModuleCode;
}

const DEFAULT_ITEMS: SidebarNavItem[] = [
  { path: '/dashboard', label: 'Dashboard compta' },
  { path: '/customers', label: 'Clients & CRM' },
  { path: '/products', label: 'Produits' },
  { path: '/quotations', label: 'Devis' },
  { path: '/invoices', label: 'Factures' },
  { path: '/payments', label: 'Paiements' },
  { path: '/maintenance', label: 'Maintenance', module: 'MAINTENANCE' },
];

@Injectable({ providedIn: 'root' })
export class SidebarNavModulesService {
  private readonly modules = inject(ModuleService);

  /** Rubriques accessibles au tenant (modules désactivés exclus). */
  readonly items = computed(() =>
    DEFAULT_ITEMS.filter((item) => !item.module || this.modules.isEnabled(item.module)),
  );

  readonly customizeMode = signal(false);

  private readonly hiddenPaths = signal<ReadonlySet<string>>(this.loadHidden());

  readonly visibleItems = computed(() =>
    this.items().filter((item) => !this.hiddenPaths().has(item.path)),
  );

  toggleCustomizeMode(): void {
    this.customizeMode.update((v) => !v);
  }

  isVisible(path: string): boolean {
    return !this.hiddenPaths().has(path);
  }

  toggleVisibility(path: string): void {
    const hidden = new Set(this.hiddenPaths());
    if (hidden.has(path)) {
      hidden.delete(path);
    } else {
      const visibleCount = this.items().length - hidden.size;
      if (visibleCount <= 1) {
        return;
      }
      hidden.add(path);
    }
    this.hiddenPaths.set(hidden);
    this.persist(hidden);
  }

  private loadHidden(): Set<string> {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return new Set();
      }
      const parsed = JSON.parse(raw) as unknown;
      if (!Array.isArray(parsed)) {
        return new Set();
      }
      return new Set(parsed.filter((p): p is string => typeof p === 'string'));
    } catch {
      return new Set();
    }
  }

  private persist(hidden: Set<string>): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([...hidden]));
  }
}
