import { Component, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { Product } from '../../shared/models/product.model';
import { formatMoney } from '../../shared/utils/money.util';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { IconActionComponent } from '../../shared/ui/icon-action.component';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { ProductsApiService } from './products-api.service';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

@Component({
  selector: 'app-products-list-page',
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
        <h1 class="text-2xl font-bold text-white">Produits</h1>
        <a
          routerLink="/products/new"
          class="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500"
        >
          <app-lucide-icon name="plus" [size]="16" />
          Nouveau produit
        </a>
      </div>

      <form class="flex flex-wrap gap-3" [formGroup]="filters" (ngSubmit)="reload()">
        <input
          type="search"
          placeholder="Rechercher…"
          formControlName="q"
          class="min-w-[200px] flex-1 rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white"
        />
        <label class="flex items-center gap-2 text-sm text-slate-400">
          <input type="checkbox" formControlName="activeOnly" class="rounded border-slate-600" />
          Actifs seulement
        </label>
        <button type="submit" class="rounded-lg border border-slate-700 px-4 py-2 text-sm text-slate-200">Filtrer</button>
      </form>

      @if (error()) {
        <app-error-banner [message]="error()!" />
      }
      @if (loading()) {
        <app-loading-state />
      } @else if (!items().length) {
        <app-empty-state title="Aucun produit." />
      } @else {
        <div class="overflow-hidden rounded-xl border border-slate-800">
          <table class="min-w-full divide-y divide-slate-800 text-sm">
            <thead class="bg-slate-900/80 text-left text-xs uppercase text-slate-500">
              <tr>
                <th class="px-4 py-3">Réf.</th>
                <th class="px-4 py-3">Nom</th>
                <th class="px-4 py-3">Prix HT</th>
                <th class="px-4 py-3">TVA</th>
                <th class="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800">
              @for (p of items(); track p.id) {
                <tr class="hover:bg-slate-900/50">
                  <td class="px-4 py-3 font-mono text-slate-400">{{ p.referenceCode }}</td>
                  <td class="px-4 py-3 text-white">{{ p.name }}</td>
                  <td class="px-4 py-3 text-slate-300">{{ money(p.unitPriceHt) }}</td>
                  <td class="px-4 py-3 text-slate-400">{{ p.vatRate }} %</td>
                  <td class="px-4 py-3">
                    <div class="flex items-center justify-end gap-1">
                      <app-icon-action
                        icon="pencil"
                        label="Modifier le produit"
                        variant="brand"
                        [link]="['/products', p.id, 'edit']"
                      />
                      <app-icon-action
                        icon="trash"
                        label="Supprimer le produit"
                        variant="danger"
                        [loading]="deletingId() === p.id"
                        (action)="confirmDelete(p)"
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
export class ProductsListPageComponent {
  private readonly api = inject(ProductsApiService);
  private readonly workspaces = inject(WorkspaceService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly items = signal<Product[]>([]);
  readonly deletingId = signal<string | null>(null);

  readonly filters = this.fb.nonNullable.group({
    q: [''],
    activeOnly: [true],
  });

  constructor() {
    effect(() => {
      this.workspaces.generation();
      this.reload();
    });
  }

  money(v: number): string {
    return formatMoney(v);
  }

  confirmDelete(p: Product): void {
    const ok = confirm(
      `Supprimer le produit « ${p.name} » (${p.referenceCode}) ?\n\nImpossible s'il est utilisé sur un devis ou un contrat.`,
    );
    if (!ok) {
      return;
    }
    this.error.set(null);
    this.deletingId.set(p.id);
    this.api.delete(p.id).subscribe({
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
    const { q, activeOnly } = this.filters.getRawValue();
    this.loading.set(true);
    this.api.search(workspaceId, { q: q || undefined, activeOnly }).subscribe({
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
