import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { QUOTATION_STATUS_LABELS, Quotation, QuotationStatus } from '../../shared/models/quotation.model';
import { formatMoney } from '../../shared/utils/money.util';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { IconActionComponent } from '../../shared/ui/icon-action.component';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { PaginatorComponent } from '../../shared/ui/paginator.component';
import { QuotationsApiService } from './quotations-api.service';
import { apiErrorMessage } from '../../shared/utils/api-error.util';
import { PageState } from '../../shared/utils/page-state';

@Component({
  selector: 'app-quotations-list-page',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    LoadingStateComponent,
    EmptyStateComponent,
    ErrorBannerComponent,
    IconActionComponent,
    LucideIconComponent,
    PaginatorComponent,
  ],
  template: `
    <div class="space-y-6">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <h1 class="text-xl font-bold text-white sm:text-2xl">Devis</h1>
        <a
          routerLink="/quotations/new"
          class="inline-flex min-h-11 items-center gap-2 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500"
        >
          <app-lucide-icon name="plus" [size]="16" />
          Nouveau devis
        </a>
      </div>
      @if (error()) {
        <app-error-banner [message]="error()!" />
      }
      @if (loading() && !items().length) {
        <app-loading-state />
      } @else if (!items().length) {
        <app-empty-state title="Aucun devis." />
      } @else {
        <div class="overflow-hidden rounded-xl border border-slate-800">
          <!-- Smartphone : cartes cliquables. -->
          <ul class="divide-y divide-slate-800 md:hidden">
            @for (q of items(); track q.id) {
              <li>
                <a [routerLink]="['/quotations', q.id]" class="flex items-center gap-3 px-3 py-3 active:bg-slate-900/60">
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-2">
                      <p class="truncate text-sm font-semibold text-white">{{ q.number ?? 'Brouillon' }}</p>
                      <span class="rounded-full px-2 py-0.5 text-[11px]" [class]="statusClass(q.status)">{{ statusLabel(q.status) }}</span>
                    </div>
                    <p class="mt-0.5 truncate text-xs text-slate-400">{{ customerName(q) }}</p>
                    <p class="mt-0.5 text-[11px] text-slate-500">{{ q.issueDate | date: 'dd/MM/yyyy' }}</p>
                  </div>
                  <p class="shrink-0 text-sm font-semibold tabular-nums text-slate-100">{{ money(q.totalTtc) }}</p>
                  <app-lucide-icon name="chevron" [size]="16" class="shrink-0 text-slate-600" />
                </a>
              </li>
            }
          </ul>
          <table class="hidden min-w-full divide-y divide-slate-800 text-sm md:table">
            <thead class="bg-slate-900/80 text-left text-xs uppercase text-slate-500">
              <tr>
                <th class="px-4 py-3">Numéro</th>
                <th class="px-4 py-3">Client</th>
                <th class="px-4 py-3">Date</th>
                <th class="px-4 py-3">Statut</th>
                <th class="px-4 py-3 text-right">Total TTC</th>
                <th class="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800">
              @for (q of items(); track q.id) {
                <tr class="hover:bg-slate-900/50">
                  <td class="px-4 py-3 text-white">{{ q.number ?? 'Brouillon' }}</td>
                  <td class="px-4 py-3 text-slate-300">{{ customerName(q) }}</td>
                  <td class="px-4 py-3 text-slate-400">{{ q.issueDate | date: 'dd/MM/yyyy' }}</td>
                  <td class="px-4 py-3">
                    <span class="rounded-full px-2 py-0.5 text-xs" [class]="statusClass(q.status)">{{ statusLabel(q.status) }}</span>
                  </td>
                  <td class="px-4 py-3 text-right tabular-nums text-slate-300">{{ money(q.totalTtc) }}</td>
                  <td class="px-4 py-3">
                    <div class="flex items-center justify-end gap-1">
                      <app-icon-action
                        icon="folder-open"
                        label="Ouvrir le devis"
                        variant="brand"
                        [link]="['/quotations', q.id]"
                      />
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
          <app-paginator
            [meta]="page.meta()"
            [disabled]="loading()"
            (pageChange)="onPageChange($event)"
            (pageSizeChange)="onPageSizeChange($event)"
          />
        </div>
      }
    </div>
  `,
})
export class QuotationsListPageComponent {
  private readonly api = inject(QuotationsApiService);
  private readonly workspaces = inject(WorkspaceService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly items = signal<Quotation[]>([]);
  readonly page = new PageState();

  constructor() {
    effect(() => {
      this.workspaces.generation();
      this.page.reset();
      this.reload();
    });
  }

  money(v: number): string {
    return formatMoney(v);
  }

  customerName(q: Quotation): string {
    return q.customerName?.trim() || 'Client non renseigné';
  }

  statusLabel(status: QuotationStatus): string {
    return QUOTATION_STATUS_LABELS[status] ?? status;
  }

  statusClass(status: QuotationStatus): string {
    switch (status) {
      case 'ACCEPTED':
        return 'bg-emerald-500/10 text-emerald-300 border border-emerald-500/20';
      case 'CONVERTED':
        return 'bg-cyan-500/10 text-cyan-300 border border-cyan-500/20';
      case 'SENT':
        return 'bg-indigo-500/10 text-indigo-300 border border-indigo-500/20';
      case 'REJECTED':
      case 'EXPIRED':
        return 'bg-rose-500/10 text-rose-300 border border-rose-500/20';
      default:
        return 'bg-slate-800 text-slate-300 border border-slate-700/60';
    }
  }

  onPageChange(index: number): void {
    this.page.goTo(index);
    this.reload();
  }

  onPageSizeChange(size: number): void {
    this.page.setSize(size);
    this.reload();
  }

  reload(): void {
    const workspaceId = this.workspaces.selectedId();
    if (!workspaceId) {
      return;
    }
    const gen = this.workspaces.generation();
    this.loading.set(true);
    this.api.list(workspaceId, this.page.query()).subscribe({
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
        this.error.set(apiErrorMessage(err, 'Erreur'));
        this.loading.set(false);
      },
    });
  }
}
