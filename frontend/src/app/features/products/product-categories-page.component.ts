import { Component, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { ProductCategory } from '../../shared/models/product-category.model';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { IconActionComponent } from '../../shared/ui/icon-action.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { ProductCategoriesApiService } from './product-categories-api.service';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

@Component({
  selector: 'app-product-categories-page',
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
        <div>
          <h1 class="text-2xl font-bold text-white">Catégories produits</h1>
          <p class="mt-1 text-sm text-slate-400">Regroupez le catalogue par famille (prestations, matériel, etc.).</p>
        </div>
        <a routerLink="/products" class="text-sm text-slate-400 hover:text-white">← Produits</a>
      </div>

      @if (error()) {
        <app-error-banner [message]="error()!" />
      }

      <form
        class="space-y-4 rounded-xl border border-slate-800 bg-slate-900/50 p-6"
        [formGroup]="form"
        (ngSubmit)="save()"
      >
        <h2 class="text-sm font-semibold uppercase tracking-wide text-slate-500">
          {{ editingId() ? 'Modifier la catégorie' : 'Nouvelle catégorie' }}
        </h2>
        <div class="grid gap-4 sm:grid-cols-3">
          <div>
            <label class="mb-1 block text-xs text-slate-500">Code</label>
            <input
              formControlName="code"
              class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 font-mono text-sm text-white"
              placeholder="CADRAGE"
            />
          </div>
          <div class="sm:col-span-2">
            <label class="mb-1 block text-xs text-slate-500">Libellé</label>
            <input
              formControlName="label"
              class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
            />
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">Ordre d'affichage</label>
            <input
              type="number"
              formControlName="sortOrder"
              class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
            />
          </div>
        </div>
        <div class="flex flex-wrap gap-2">
          <button
            type="submit"
            class="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500 disabled:opacity-50"
            [disabled]="form.invalid || saving()"
          >
            <app-lucide-icon name="check" [size]="16" />
            {{ editingId() ? 'Mettre à jour' : 'Créer' }}
          </button>
          @if (editingId()) {
            <button
              type="button"
              class="rounded-lg border border-slate-700 px-4 py-2 text-sm text-slate-300 hover:bg-slate-800"
              (click)="cancelEdit()"
            >
              Annuler
            </button>
          }
        </div>
      </form>

      @if (loading()) {
        <app-loading-state />
      } @else if (!items().length) {
        <app-empty-state title="Aucune catégorie." />
      } @else {
        <div class="overflow-hidden rounded-xl border border-slate-800">
          <table class="min-w-full divide-y divide-slate-800 text-sm">
            <thead class="bg-slate-900/80 text-left text-xs uppercase text-slate-500">
              <tr>
                <th class="px-4 py-3">Ordre</th>
                <th class="px-4 py-3">Code</th>
                <th class="px-4 py-3">Libellé</th>
                <th class="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800">
              @for (c of items(); track c.id) {
                <tr class="hover:bg-slate-900/50">
                  <td class="px-4 py-3 text-slate-400">{{ c.sortOrder }}</td>
                  <td class="px-4 py-3 font-mono text-slate-400">{{ c.code }}</td>
                  <td class="px-4 py-3 text-white">{{ c.label }}</td>
                  <td class="px-4 py-3">
                    <div class="flex items-center justify-end gap-1">
                      <app-icon-action icon="pencil" label="Modifier" variant="brand" (action)="startEdit(c)" />
                      <app-icon-action
                        icon="trash"
                        label="Supprimer"
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
export class ProductCategoriesPageComponent {
  private readonly api = inject(ProductCategoriesApiService);
  private readonly workspaces = inject(WorkspaceService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly items = signal<ProductCategory[]>([]);
  readonly editingId = signal<string | null>(null);
  readonly deletingId = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    code: ['', Validators.required],
    label: ['', Validators.required],
    sortOrder: [0, [Validators.required, Validators.min(0)]],
  });

  constructor() {
    effect(() => {
      this.workspaces.generation();
      this.reload();
    });
  }

  reload(): void {
    const workspaceId = this.workspaces.selectedId();
    if (!workspaceId) {
      return;
    }
    const gen = this.workspaces.generation();
    this.loading.set(true);
    this.api.list(workspaceId).subscribe({
      next: (list) => {
        if (this.workspaces.isStale(gen)) {
          return;
        }
        this.items.set(list);
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

  startEdit(c: ProductCategory): void {
    this.editingId.set(c.id);
    this.form.patchValue({ code: c.code, label: c.label, sortOrder: c.sortOrder });
    this.error.set(null);
  }

  cancelEdit(): void {
    this.editingId.set(null);
    this.form.reset({ code: '', label: '', sortOrder: 0 });
  }

  save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    const workspaceId = this.workspaces.requireSelectedId();
    const payload = { workspaceId, ...this.form.getRawValue() };
    const id = this.editingId();
    this.saving.set(true);
    this.error.set(null);
    const req = id ? this.api.update(id, payload) : this.api.create(payload);
    req.subscribe({
      next: () => {
        this.saving.set(false);
        this.cancelEdit();
        this.reload();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Enregistrement impossible'));
      },
    });
  }

  confirmDelete(c: ProductCategory): void {
    const ok = confirm(`Supprimer la catégorie « ${c.label} » (${c.code}) ?\n\nImpossible si des produits y sont rattachés.`);
    if (!ok) {
      return;
    }
    this.deletingId.set(c.id);
    this.api.delete(c.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        if (this.editingId() === c.id) {
          this.cancelEdit();
        }
        this.reload();
      },
      error: (err) => {
        this.deletingId.set(null);
        this.error.set(apiErrorMessage(err, 'Suppression impossible'));
      },
    });
  }
}
