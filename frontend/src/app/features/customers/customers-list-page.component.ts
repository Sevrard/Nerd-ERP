import { DatePipe } from '@angular/common';
import { Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { CsvImportDialogComponent } from '../../shared/ui/csv-import-dialog.component';
import {
  Customer,
  CustomerPayload,
  CustomerStats,
  CustomerStatus,
  customerDisplayName,
} from '../../shared/models/customer.model';
import { sortParam } from '../../shared/models/page.model';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { IconActionComponent } from '../../shared/ui/icon-action.component';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { PaginatorComponent } from '../../shared/ui/paginator.component';
import { CustomersApiService } from './customers-api.service';
import { apiErrorMessage } from '../../shared/utils/api-error.util';
import { PageState } from '../../shared/utils/page-state';
import { SortableThComponent } from '../../shared/ui/sortable-th.component';
import { nextSortDirection, SortDirection } from '../../shared/utils/table-sort.util';

type CustomerSortKey = 'name' | 'email' | 'location' | 'status';

/** Colonnes UI → propriétés JPA triables côté serveur (plusieurs propriétés = tri composé). */
const SORT_PROPERTIES: Record<CustomerSortKey, string> = {
  name: 'companyName,lastName,firstName',
  email: 'email',
  location: 'postalCode,city',
  status: 'status',
};

const EMPTY_STATS: CustomerStats = { total: 0, prospects: 0, clients: 0, followUpsDue: 0 };

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
    PaginatorComponent,
    CsvImportDialogComponent,
  ],
  templateUrl: './customers-list-page.component.html',
})
export class CustomersListPageComponent {
  private readonly api = inject(CustomersApiService);
  readonly auth = inject(AuthService);

  readonly importOpen = signal(false);
  readonly importCustomers = (workspaceId: string, file: File, dryRun: boolean) =>
    this.api.importCsv(workspaceId, file, dryRun);
  readonly importTemplate = () => this.api.importTemplate();

  onImported(): void {
    this.importOpen.set(false);
    this.page.reset();
    this.reloadAll();
  }
  private readonly workspaces = inject(WorkspaceService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searchInput$ = new Subject<string>();

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly items = signal<Customer[]>([]);
  readonly stats = signal<CustomerStats>(EMPTY_STATS);
  readonly page = new PageState();
  readonly deletingId = signal<string | null>(null);

  readonly filterQ = signal('');
  readonly filterStatus = signal<'' | CustomerStatus>('');

  readonly customerSortKey = signal<CustomerSortKey>('name');
  readonly customerSortDir = signal<SortDirection>('asc');
  readonly editingCustomerId = signal<string | null>(null);
  readonly customerEditDraft = signal<CustomerInlineDraft | null>(null);
  readonly savingCustomerId = signal<string | null>(null);

  constructor() {
    this.searchInput$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((q) => {
        this.filterQ.set(q);
        this.page.reset();
        this.reload();
      });

    effect(() => {
      this.workspaces.generation();
      this.page.reset();
      this.reloadAll();
    });
  }

  label(c: Customer): string {
    return customerDisplayName(c);
  }

  onSearchInput(value: string): void {
    this.searchInput$.next(value);
  }

  clearSearch(): void {
    this.searchInput$.next('');
  }

  onStatusFilterChange(value: string): void {
    this.filterStatus.set(value as '' | CustomerStatus);
    this.page.reset();
    this.reload();
  }

  onCustomerSort(key: string): void {
    const k = key as CustomerSortKey;
    this.customerSortDir.set(nextSortDirection(this.customerSortKey(), k, this.customerSortDir()));
    this.customerSortKey.set(k);
    this.page.reset();
    this.reload();
  }

  onPageChange(index: number): void {
    this.page.goTo(index);
    this.reload();
  }

  onPageSizeChange(size: number): void {
    this.page.setSize(size);
    this.reload();
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
        this.reloadAll();
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
        this.page.adjustAfterRemoval();
        this.reloadAll();
      },
      error: (err) => {
        this.deletingId.set(null);
        this.error.set(apiErrorMessage(err, 'Suppression impossible'));
      },
    });
  }

  /** Liste + compteurs globaux (après création / modification / suppression, changement de workspace). */
  reloadAll(): void {
    this.reloadStats();
    this.reload();
  }

  /** Page courante uniquement (navigation, filtre, tri). */
  reload(): void {
    const workspaceId = this.workspaces.selectedId();
    if (!workspaceId) {
      return;
    }
    const gen = this.workspaces.generation();
    this.loading.set(true);
    this.error.set(null);
    const sort = sortParam(SORT_PROPERTIES[this.customerSortKey()], this.customerSortDir());
    this.api
      .search(workspaceId, {
        q: this.filterQ().trim() || undefined,
        status: this.filterStatus() || undefined,
        ...this.page.query(sort),
      })
      .subscribe({
        next: (page) => {
          if (this.workspaces.isStale(gen)) {
            return;
          }
          this.items.set(page.content);
          this.page.apply(page);
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

  private reloadStats(): void {
    const workspaceId = this.workspaces.selectedId();
    if (!workspaceId) {
      return;
    }
    const gen = this.workspaces.generation();
    this.api.stats(workspaceId).subscribe({
      next: (stats) => {
        if (!this.workspaces.isStale(gen)) {
          this.stats.set(stats);
        }
      },
      error: () => {
        if (!this.workspaces.isStale(gen)) {
          this.stats.set(EMPTY_STATS);
        }
      },
    });
  }
}
