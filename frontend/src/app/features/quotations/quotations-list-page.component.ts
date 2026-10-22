import { Component, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { Quotation } from '../../shared/models/quotation.model';
import { formatMoney } from '../../shared/utils/money.util';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { IconActionComponent } from '../../shared/ui/icon-action.component';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { QuotationsApiService } from './quotations-api.service';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

@Component({
  selector: 'app-quotations-list-page',
  standalone: true,
  imports: [
    RouterLink,
    LoadingStateComponent,
    EmptyStateComponent,
    ErrorBannerComponent,
    IconActionComponent,
    LucideIconComponent,
  ],
  template: `
    <div class="space-y-6">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <h1 class="text-2xl font-bold text-white">Devis</h1>
        <a
          routerLink="/quotations/new"
          class="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500"
        >
          <app-lucide-icon name="plus" [size]="16" />
          Nouveau devis
        </a>
      </div>
      @if (error()) {
        <app-error-banner [message]="error()!" />
      }
      @if (loading()) {
        <app-loading-state />
      } @else if (!items().length) {
        <app-empty-state title="Aucun devis." />
      } @else {
        <div class="overflow-hidden rounded-xl border border-slate-800">
          <table class="min-w-full divide-y divide-slate-800 text-sm">
            <thead class="bg-slate-900/80 text-left text-xs uppercase text-slate-500">
              <tr>
                <th class="px-4 py-3">Numéro</th>
                <th class="px-4 py-3">Statut</th>
                <th class="px-4 py-3">Total TTC</th>
                <th class="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800">
              @for (q of items(); track q.id) {
                <tr class="hover:bg-slate-900/50">
                  <td class="px-4 py-3 text-white">{{ q.number ?? 'Brouillon' }}</td>
                  <td class="px-4 py-3">
                    <span class="rounded-full bg-slate-800 px-2 py-0.5 text-xs">{{ q.status }}</span>
                  </td>
                  <td class="px-4 py-3 text-slate-300">{{ money(q.totalTtc) }}</td>
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

  constructor() {
    effect(() => {
      this.workspaces.generation();
      this.reload();
    });
  }

  money(v: number): string {
    return formatMoney(v);
  }

  reload(): void {
    const workspaceId = this.workspaces.selectedId();
    if (!workspaceId) {
      return;
    }
    const gen = this.workspaces.generation();
    this.loading.set(true);
    this.api.list(workspaceId).subscribe({
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
        this.error.set(apiErrorMessage(err, 'Erreur'));
        this.loading.set(false);
      },
    });
  }
}
