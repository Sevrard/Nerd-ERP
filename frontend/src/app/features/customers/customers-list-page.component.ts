import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import {
  Customer,
  CustomerPayload,
  CustomerStatus,
  customerDisplayName,
} from '../../shared/models/customer.model';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { IconActionComponent } from '../../shared/ui/icon-action.component';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { CustomersApiService } from './customers-api.service';
import { apiErrorMessage } from '../../shared/utils/api-error.util';
import { SortableThComponent } from '../../shared/ui/sortable-th.component';
import {
  nextSortDirection,
  sortMultiplier,
  SortDirection,
} from '../../shared/utils/table-sort.util';
type CustomerSortKey = 'name' | 'email' | 'location' | 'status';

interface CustomerInlineDraft {
  companyName: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  postalCode: string;
  city: string;
  status: CustomerStatus;
}

@Component({
  selector: 'app-customers-list-page',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    LoadingStateComponent,
    EmptyStateComponent,
    ErrorBannerComponent,
    IconActionComponent,
    LucideIconComponent,
    SortableThComponent,
  ],
  templateUrl: './customers-list-page.component.html',
})
export class CustomersListPageComponent {
  private readonly api = inject(CustomersApiService);
  private readonly workspaces = inject(WorkspaceService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly items = signal<Customer[]>([]);
  readonly deletingId = signal<string | null>(null);

  readonly filterQ = signal('');
  readonly filterStatus = signal<'' | CustomerStatus>('');

  readonly customerSortKey = signal<CustomerSortKey>('name');
  readonly customerSortDir = signal<SortDirection>('asc');
  readonly editingCustomerId = signal<string | null>(null);
  readonly customerEditDraft = signal<CustomerInlineDraft | null>(null);
  readonly savingCustomerId = signal<string | null>(null);

  readonly stats = computed(() => {
    const all = this.items().filter((c) => !c.archived);
    const now = Date.now();
    return {
      total: all.length,
      prospects: all.filter((c) => c.status === 'PROSPECT').length,
      clients: all.filter((c) => c.status === 'CLIENT').length,
      followUpsDue: all.filter((c) => c.nextFollowUpAt && new Date(c.nextFollowUpAt).getTime() <= now).length,
    };
  });

  readonly filteredCustomers = computed(() => {
    const q = this.filterQ().trim().toLowerCase();
    const status = this.filterStatus();
    return this.items()
      .filter((c) => !c.archived)
      .filter((c) => {
        if (status && c.status !== status) {
          return false;
        }
        if (!q) {
          return true;
        }
        const haystack = [
          customerDisplayName(c),
          c.email ?? '',
          c.phone ?? '',
          c.city ?? '',
          c.companyName ?? '',
          c.firstName ?? '',
          c.lastName ?? '',
        ]
          .join(' ')
          .toLowerCase();
        return haystack.includes(q);
      });
  });

  readonly sortedFilteredCustomers = computed(() => {
    const list = [...this.filteredCustomers()];
    const key = this.customerSortKey();
    const mul = sortMultiplier(this.customerSortDir());
    list.sort((a, b) => {
      let cmp = 0;
      switch (key) {
        case 'name':
          cmp = customerDisplayName(a).localeCompare(customerDisplayName(b), 'fr');
          break;
        case 'email':
          cmp = (a.email ?? '').localeCompare(b.email ?? '', 'fr');
          break;
        case 'location':
          cmp = this.locationLine(a).localeCompare(this.locationLine(b), 'fr');
          break;
        case 'status':
          cmp = a.status.localeCompare(b.status);
          break;
      }
      return cmp * mul;
    });
    return list;
  });

  constructor() {
    effect(() => {
      this.workspaces.generation();
      this.reload();
    });
  }

  label(c: Customer): string {
    return customerDisplayName(c);
  }

  onSearchInput(value: string): void {
    this.filterQ.set(value);
  }

  clearSearch(): void {
    this.filterQ.set('');
  }

  onStatusFilterChange(value: string): void {
    this.filterStatus.set(value as '' | CustomerStatus);
  }

  onCustomerSort(key: string): void {
    const k = key as CustomerSortKey;
    this.customerSortDir.set(nextSortDirection(this.customerSortKey(), k, this.customerSortDir()));
    this.customerSortKey.set(k);
  }

  startCustomerInlineEdit(c: Customer): void {
    this.editingCustomerId.set(c.id);
    this.customerEditDraft.set({
      companyName: c.companyName ?? '',
      firstName: c.firstName ?? '',
      lastName: c.lastName ?? '',
      email: c.email ?? '',
      phone: c.phone ?? '',
      postalCode: c.postalCode ?? '',
      city: c.city ?? '',
      status: c.status,
    });
  }

  cancelCustomerInlineEdit(): void {
    this.editingCustomerId.set(null);
    this.customerEditDraft.set(null);
  }

  patchCustomerDraft(partial: Partial<CustomerInlineDraft>): void {
    const cur = this.customerEditDraft();
    if (cur) {
      this.customerEditDraft.set({ ...cur, ...partial });
    }
  }

  saveCustomerInlineEdit(c: Customer): void {
    const draft = this.customerEditDraft();
    if (!draft) {
      return;
    }
    if (c.customerType === 'COMPANY' && !draft.companyName.trim()) {
      return;
    }
    if (c.customerType === 'INDIVIDUAL' && !draft.firstName.trim() && !draft.lastName.trim()) {
      return;
    }
    const payload: CustomerPayload = {
      workspaceId: c.workspaceId,
      customerType: c.customerType,
      companyName: c.customerType === 'COMPANY' ? draft.companyName.trim() || null : c.companyName,
      firstName: c.customerType === 'INDIVIDUAL' ? draft.firstName.trim() || null : c.firstName,
      lastName: c.customerType === 'INDIVIDUAL' ? draft.lastName.trim() || null : c.lastName,
      email: draft.email.trim() || null,
      phone: draft.phone.trim() || null,
      addressLine1: c.addressLine1,
      addressLine2: c.addressLine2,
      postalCode: draft.postalCode.trim() || null,
      city: draft.city.trim() || null,
      countryCode: c.countryCode,
      status: draft.status,
      notes: c.notes,
      nextFollowUpAt: c.nextFollowUpAt,
    };
    this.savingCustomerId.set(c.id);
    this.api.update(c.id, payload).subscribe({
      next: () => {
        this.savingCustomerId.set(null);
        this.cancelCustomerInlineEdit();
        this.reload();
      },
      error: (err) => {
        this.savingCustomerId.set(null);
        this.error.set(apiErrorMessage(err, 'Mise à jour impossible'));
      },
    });
  }

  statusLabel(status: CustomerStatus): string {
    switch (status) {
      case 'PROSPECT':
        return 'Prospect';
      case 'CLIENT':
        return 'Client';
      case 'INACTIVE':
        return 'Inactif';
    }
  }

  statusBadgeClass(status: CustomerStatus): string {
    switch (status) {
      case 'PROSPECT':
        return 'bg-cyan-500/10 text-cyan-400 border-cyan-500/20';
      case 'CLIENT':
        return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20';
      case 'INACTIVE':
        return 'bg-slate-700/40 text-slate-400 border-slate-600/40';
    }
  }

  typeLabel(c: Customer): string {
    return c.customerType === 'COMPANY' ? 'Entreprise' : 'Particulier';
  }

  locationLine(c: Customer): string {
    const parts = [c.postalCode, c.city].filter(Boolean);
    return parts.length ? parts.join(' ') : '—';
  }

  confirmDelete(c: Customer): void {
    const ok = confirm(
      `Supprimer définitivement ${this.label(c)} ?\n\nImpossible si des devis, factures, paiements ou contrats de maintenance existent.`,
    );
    if (!ok) {
      return;
    }
    this.error.set(null);
    this.deletingId.set(c.id);
    this.api.delete(c.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.reload();
      },
      error: (err) => {
        this.deletingId.set(null);
        this.error.set(apiErrorMessage(err, 'Suppression impossible'));
      },
    });
  }

  reload(): void {
    const workspaceId = this.workspaces.selectedId();
    if (!workspaceId) {
      return;
    }
    const gen = this.workspaces.generation();
    this.loading.set(true);
    this.error.set(null);
    this.api.search(workspaceId, { size: 500 }).subscribe({
      next: (page) => {
        if (this.workspaces.isStale(gen)) {
          return;
        }
        this.items.set(page.content);
        this.loading.set(false);
      },
      error: (err) => {
        if (this.workspaces.isStale(gen)) {
          return;
        }
        this.error.set(apiErrorMessage(err, 'Erreur de chargement'));
        this.loading.set(false);
      },
    });
  }
}
