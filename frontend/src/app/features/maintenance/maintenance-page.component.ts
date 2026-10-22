import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { CustomersApiService } from '../customers/customers-api.service';
import { ProductsApiService } from '../products/products-api.service';
import { Customer, customerDisplayName } from '../../shared/models/customer.model';
import {
  MaintenanceContract,
  MaintenanceContractStatus,
  MaintenancePeriodicity,
} from '../../shared/models/maintenance.model';
import { Product } from '../../shared/models/product.model';
import { formatMoney } from '../../shared/utils/money.util';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { MaintenanceApiService } from './maintenance-api.service';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

@Component({
  selector: 'app-maintenance-page',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe, LoadingStateComponent, EmptyStateComponent, ErrorBannerComponent],
  template: `
    <div class="space-y-8">
      <h1 class="text-2xl font-bold text-white">Maintenance</h1>

      <section class="rounded-xl border border-slate-800 bg-slate-900/50 p-6">
        <h2 class="mb-4 text-lg font-semibold text-white">Nouveau contrat</h2>
        @if (formError()) {
          <app-error-banner class="mb-4 block" [message]="formError()!" />
        }
        <form class="grid gap-4 sm:grid-cols-2" [formGroup]="form" (ngSubmit)="create()">
          <div>
            <label class="mb-1 block text-xs text-slate-500">Client</label>
            <select formControlName="customerId" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white">
              <option value="">— Choisir —</option>
              @for (c of customers(); track c.id) {
                <option [value]="c.id">{{ label(c) }}</option>
              }
            </select>
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">Produit (optionnel)</label>
            <select formControlName="productId" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white">
              <option value="">— Aucun —</option>
              @for (p of products(); track p.id) {
                <option [value]="p.id">{{ p.name }}</option>
              }
            </select>
          </div>
          <div class="sm:col-span-2">
            <label class="mb-1 block text-xs text-slate-500">Titre</label>
            <input formControlName="title" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
          </div>
          <div class="sm:col-span-2">
            <label class="mb-1 block text-xs text-slate-500">Description</label>
            <textarea formControlName="description" rows="2" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"></textarea>
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">Périodicité</label>
            <select formControlName="periodicity" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white">
              <option value="MONTHLY">Mensuel</option>
              <option value="QUARTERLY">Trimestriel</option>
              <option value="YEARLY">Annuel</option>
            </select>
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">Statut</label>
            <select formControlName="status" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white">
              <option value="DRAFT">Brouillon</option>
              <option value="ACTIVE">Actif</option>
              <option value="SUSPENDED">Suspendu</option>
              <option value="TERMINATED">Résilié</option>
              <option value="RENEWAL_DUE">Renouvellement dû</option>
            </select>
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">Montant HT</label>
            <input type="number" step="0.01" formControlName="amountHt" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">TVA %</label>
            <input type="number" step="0.01" formControlName="vatRate" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">Début</label>
            <input type="date" formControlName="startDate" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">Prochain renouvellement</label>
            <input type="date" formControlName="nextRenewalDate" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
          </div>
          <button type="submit" class="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500 sm:col-span-2 sm:w-fit" [disabled]="form.invalid || saving()">
            Créer
          </button>
        </form>
      </section>

      <section class="space-y-4">
        <h2 class="text-lg font-semibold text-white">Contrats</h2>
        @if (listError()) {
          <app-error-banner [message]="listError()!" />
        }
        @if (loading()) {
          <app-loading-state />
        } @else if (!contracts().length) {
          <app-empty-state title="Aucun contrat de maintenance." />
        } @else {
          <div class="overflow-hidden rounded-xl border border-slate-800">
            <table class="min-w-full divide-y divide-slate-800 text-sm">
              <thead class="bg-slate-900/80 text-left text-xs uppercase text-slate-500">
                <tr>
                  <th class="px-4 py-3">Titre</th>
                  <th class="px-4 py-3">Statut</th>
                  <th class="px-4 py-3">Montant HT</th>
                  <th class="px-4 py-3">Renouvellement</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-800">
                @for (c of contracts(); track c.id) {
                  <tr>
                    <td class="px-4 py-3 text-white">{{ c.title }}</td>
                    <td class="px-4 py-3 text-slate-400">{{ c.status }}</td>
                    <td class="px-4 py-3">{{ money(c.amountHt) }}</td>
                    <td class="px-4 py-3 text-slate-400">{{ c.nextRenewalDate | date: 'shortDate' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
    </div>
  `,
})
export class MaintenancePageComponent {
  private readonly api = inject(MaintenanceApiService);
  private readonly customersApi = inject(CustomersApiService);
  private readonly productsApi = inject(ProductsApiService);
  private readonly workspaces = inject(WorkspaceService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly listError = signal<string | null>(null);
  readonly formError = signal<string | null>(null);
  readonly contracts = signal<MaintenanceContract[]>([]);
  readonly customers = signal<Customer[]>([]);
  readonly products = signal<Product[]>([]);

  readonly form = this.fb.nonNullable.group({
    customerId: ['', Validators.required],
    productId: [''],
    title: ['', Validators.required],
    description: [''],
    periodicity: ['YEARLY' as MaintenancePeriodicity],
    amountHt: [0, [Validators.required, Validators.min(0)]],
    vatRate: [20],
    startDate: [new Date().toISOString().slice(0, 10), Validators.required],
    endDate: [''],
    nextRenewalDate: [''],
    status: ['DRAFT' as MaintenanceContractStatus],
  });

  constructor() {
    effect(() => {
      this.workspaces.generation();
      const workspaceId = this.workspaces.selectedId();
      if (!workspaceId) {
        return;
      }
      this.customersApi.search(workspaceId, {}).subscribe((page) => this.customers.set(page.content));
      this.productsApi.search(workspaceId, {}).subscribe((page) => this.products.set(page.content));
      this.reload();
    });
  }

  label(c: Customer): string {
    return customerDisplayName(c);
  }

  money(v: number): string {
    return formatMoney(v);
  }

  create(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.formError.set(null);
    this.api
      .create({
        workspaceId: this.workspaces.requireSelectedId(),
        customerId: v.customerId,
        productId: v.productId || null,
        title: v.title,
        description: v.description || null,
        periodicity: v.periodicity,
        amountHt: Number(v.amountHt),
        vatRate: Number(v.vatRate),
        startDate: v.startDate,
        endDate: v.endDate || null,
        nextRenewalDate: v.nextRenewalDate || null,
        status: v.status,
      })
      .subscribe({
        next: () => {
          this.form.reset({
            customerId: '',
            productId: '',
            title: '',
            description: '',
            periodicity: 'YEARLY',
            amountHt: 0,
            vatRate: 20,
            startDate: new Date().toISOString().slice(0, 10),
            endDate: '',
            nextRenewalDate: '',
            status: 'DRAFT',
          });
          this.reload();
          this.saving.set(false);
        },
        error: (err) => {
          this.formError.set(apiErrorMessage(err, 'Création impossible'));
          this.saving.set(false);
        },
      });
  }

  private reload(): void {
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
        this.contracts.set(page.content);
        this.loading.set(false);
      },
      error: (err) => {
        if (this.workspaces.isStale(gen)) {
          return;
        }
        this.listError.set(apiErrorMessage(err, 'Erreur'));
        this.loading.set(false);
      },
    });
  }
}
