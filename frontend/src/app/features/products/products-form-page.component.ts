import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { ProductPayload } from '../../shared/models/product.model';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { ProductsApiService } from './products-api.service';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

@Component({
  selector: 'app-products-form-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ErrorBannerComponent, LoadingStateComponent],
  template: `
    <div class="mx-auto max-w-lg space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-white">{{ editId ? 'Modifier le produit' : 'Nouveau produit' }}</h1>
        <a routerLink="/products" class="text-sm text-slate-400 hover:text-white">Retour</a>
      </div>
      @if (error()) {
        <app-error-banner [message]="error()!" />
      }
      @if (loading()) {
        <app-loading-state />
      } @else {
        <form class="space-y-4 rounded-xl border border-slate-800 bg-slate-900/50 p-6" [formGroup]="form" (ngSubmit)="save()">
          <div>
            <label class="mb-1 block text-xs text-slate-500">Référence</label>
            <input formControlName="referenceCode" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">Nom</label>
            <input formControlName="name" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">Description</label>
            <textarea formControlName="description" rows="3" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"></textarea>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="mb-1 block text-xs text-slate-500">Prix HT</label>
              <input type="number" step="0.01" formControlName="unitPriceHt" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
            </div>
            <div>
              <label class="mb-1 block text-xs text-slate-500">TVA %</label>
              <input type="number" step="0.01" formControlName="vatRate" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
            </div>
          </div>
          <label class="flex items-center gap-2 text-sm text-slate-300">
            <input type="checkbox" formControlName="active" />
            Actif
          </label>
          <button type="submit" class="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500" [disabled]="form.invalid || saving()">
            Enregistrer
          </button>
        </form>
      }
    </div>
  `,
})
export class ProductsFormPageComponent implements OnInit {
  private readonly api = inject(ProductsApiService);
  private readonly workspaces = inject(WorkspaceService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  editId: string | null = null;
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    referenceCode: ['', Validators.required],
    name: ['', Validators.required],
    description: [''],
    unitPriceHt: [0, [Validators.required, Validators.min(0)]],
    vatRate: [20, [Validators.required, Validators.min(0)]],
    active: [true],
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id && this.route.snapshot.url.some((s) => s.path === 'edit')) {
      this.editId = id;
      this.loading.set(true);
      this.api.get(id).subscribe({
        next: (p) => {
          this.form.patchValue({
            referenceCode: p.referenceCode,
            name: p.name,
            description: p.description ?? '',
            unitPriceHt: p.unitPriceHt,
            vatRate: p.vatRate,
            active: p.active,
          });
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(apiErrorMessage(err, 'Produit introuvable'));
          this.loading.set(false);
        },
      });
    }
  }

  save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    const payload: ProductPayload = {
      workspaceId: this.workspaces.requireSelectedId(),
      ...this.form.getRawValue(),
      description: this.form.value.description || null,
    };
    this.saving.set(true);
    const req = this.editId ? this.api.update(this.editId, payload) : this.api.create(payload);
    req.subscribe({
      next: () => void this.router.navigate(['/products']),
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Enregistrement impossible'));
        this.saving.set(false);
      },
      complete: () => this.saving.set(false),
    });
  }
}
