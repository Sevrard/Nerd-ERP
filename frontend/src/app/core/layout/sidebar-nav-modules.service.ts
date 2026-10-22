import { Injectable, computed, signal } from '@angular/core';

const STORAGE_KEY = 'nerd-erp-nav-modules-visible';

export interface SidebarNavItem {
  path: string;
  label: string;
}

const DEFAULT_ITEMS: SidebarNavItem[] = [
  { path: '/dashboard', label: 'Dashboard compta' },
  { path: '/customers', label: 'Clients & CRM' },
  { path: '/products', label: 'Produits' },
  { path: '/quotations', label: 'Devis' },
  { path: '/invoices', label: 'Factures' },
  { path: '/payments', label: 'Paiements' },
  { path: '/maintenance', label: 'Maintenance' },
];

@Injectable({ providedIn: 'root' })
export class SidebarNavModulesService {
  readonly items = DEFAULT_ITEMS;

  readonly customizeMode = signal(false);

  private readonly hiddenPaths = signal<ReadonlySet<string>>(this.loadHidden());

  readonly visibleItems = computed(() =>
    this.items.filter((item) => !this.hiddenPaths().has(item.path)),
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
      const visibleCount = this.items.length - hidden.size;
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
