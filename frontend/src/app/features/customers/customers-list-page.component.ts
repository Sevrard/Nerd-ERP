import { Component, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { Customer, CustomerStatus, customerDisplayName } from '../../shared/models/customer.model';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { IconActionComponent } from '../../shared/ui/icon-action.component';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { CustomersApiService } from './customers-api.service';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

@Component({
  selector: 'app-customers-list-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
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
        <h1 class="text-2xl font-bold text-white">Clients & CRM</h1>
        <a
          routerLink="/customers/new"
          class="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500"
        >
          <app-lucide-icon name="plus" [size]="16" />
          Nouveau client
        </a>
      </div>

      <form class="flex flex-wrap gap-3" [formGroup]="filters" (ngSubmit)="reload()">
        <input
          type="search"
          placeholder="Rechercher…"
          formControlName="q"
          class="min-w-[200px] flex-1 rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white"
        />
        <select
          formControlName="status"
          class="rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white"
        >
          <option value="">Tous les statuts</option>
          <option value="PROSPECT">Prospect</option>
          <option value="CLIENT">Client</option>
          <option value="INACTIVE">Inactif</option>
        </select>
        <button type="submit" class="rounded-lg border border-slate-700 px-4 py-2 text-sm text-slate-200 hover:bg-slate-800">
          Filtrer
        </button>
      </form>

      @if (error()) {
        <app-error-banner [message]="error()!" />
      }
      @if (loading()) {
        <app-loading-state />
      } @else if (!items().length) {
        <app-empty-state title="Aucun client trouvé." />
      } @else {
        <div class="overflow-hidden rounded-xl border border-slate-800">
          <table class="min-w-full divide-y divide-slate-800 text-sm">
            <thead class="bg-slate-900/80 text-left text-xs uppercase text-slate-500">
              <tr>
                <th class="px-4 py-3">Nom</th>
                <th class="px-4 py-3">E-mail</th>
                <th class="px-4 py-3">Statut</th>
                <th class="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800 bg-slate-950/40">
              @for (c of items(); track c.id) {
                <tr class="hover:bg-slate-900/50">
                  <td class="px-4 py-3 font-medium text-white">{{ label(c) }}</td>
                  <td class="px-4 py-3 text-slate-400">{{ c.email ?? '—' }}</td>
                  <td class="px-4 py-3">
                    <span class="rounded-full bg-slate-800 px-2 py-0.5 text-xs text-slate-300">{{ c.status }}</span>
                  </td>
                  <td class="px-4 py-3">
                    <div class="flex items-center justify-end gap-1">
                      <app-icon-action
                        icon="eye"
                        label="Voir le client"
                        variant="brand"
                        [link]="['/customers', c.id]"
                      />
                      <app-icon-action
                        icon="pencil"
                        label="Modifier le client"
                        variant="default"
                        [link]="['/customers', c.id, 'edit']"
                      />
                      <app-icon-action
                        icon="trash"
                        label="Supprimer le client"
                        variant="danger"
                        [loading]="deletingId() === c.id"
                        (action)="confirmDelete(c)"
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
export class CustomersListPageComponent {
  private readonly api = inject(CustomersApiService);
  private readonly workspaces = inject(WorkspaceService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly items = signal<Customer[]>([]);
  readonly deletingId = signal<string | null>(null);

  readonly filters = this.fb.nonNullable.group({
    q: [''],
    status: ['' as '' | CustomerStatus],
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
    const { q, status } = this.filters.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    this.api.search(workspaceId, { q: q || undefined, status: status || undefined }).subscribe({
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
