import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, map, of } from 'rxjs';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { customerPickerSource } from '../customers/customer-picker.util';
import { CustomersApiService } from '../customers/customers-api.service';
import { InvoicesApiService } from '../invoices/invoices-api.service';
import { Invoice } from '../../shared/models/invoice.model';
import { Payment, PaymentMethod, PaymentPayload } from '../../shared/models/payment.model';
import { formatMoney } from '../../shared/utils/money.util';
import { PageState } from '../../shared/utils/page-state';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { PaginatorComponent } from '../../shared/ui/paginator.component';
import { SEARCH_SELECT_LIMIT, SearchSelectComponent } from '../../shared/ui/search-select.component';
import { PaymentsApiService } from './payments-api.service';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

@Component({
  selector: 'app-payments-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    LoadingStateComponent,
    EmptyStateComponent,
    ErrorBannerComponent,
    PaginatorComponent,
    SearchSelectComponent,
  ],
  template: `
    <div class="space-y-8">
      <h1 class="text-2xl font-bold text-white">Paiements</h1>

      <section class="rounded-xl border border-slate-800 bg-slate-900/50 p-6">
        <h2 class="mb-4 text-lg font-semibold text-white">Enregistrer un paiement</h2>
        @if (formError()) {
          <app-error-banner class="mb-4 block" [message]="formError()!" />
        }
        <form class="space-y-4" [formGroup]="form" (ngSubmit)="submit()">
          <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <label class="mb-1 block text-xs text-slate-500">Client</label>
              <app-search-select
                formControlName="customerId"
                [search]="customerPicker.search"
                [resolve]="customerPicker.resolve"
                [labelFn]="customerPicker.label"
                [detailFn]="customerPicker.detail"
                [refreshKey]="workspaces.generation()"
                placeholder="Rechercher un client…"
              />
            </div>
            <div>
              <label class="mb-1 block text-xs text-slate-500">Montant</label>
              <input type="number" step="0.01" formControlName="amount" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
            </div>
            <div>
              <label class="mb-1 block text-xs text-slate-500">Date</label>
              <input type="date" formControlName="paymentDate" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
            </div>
            <div>
              <label class="mb-1 block text-xs text-slate-500">Méthode</label>
              <select formControlName="method" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white">
                <option value="TRANSFER">Virement</option>
                <option value="CARD">Carte</option>
                <option value="CHECK">Chèque</option>
                <option value="CASH">Espèces</option>
                <option value="OTHER">Autre</option>
              </select>
            </div>
          </div>
          <div class="grid gap-4 sm:grid-cols-2">
            <div>
              <label class="mb-1 block text-xs text-slate-500">Référence</label>
              <input formControlName="reference" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
            </div>
            <div>
              <label class="mb-1 block text-xs text-slate-500">Notes</label>
              <input formControlName="notes" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
            </div>
          </div>

          <div>
            <div class="mb-2 flex items-center justify-between">
              <h3 class="text-sm font-medium text-slate-300">Affectations factures</h3>
              <button type="button" class="text-sm text-brand-400" (click)="addAllocation()">+ Affectation</button>
            </div>
            <div class="space-y-2" formArrayName="allocations">
              @for (row of allocations.controls; track $index; let i = $index) {
                <div class="grid gap-2 sm:grid-cols-3" [formGroupName]="i">
                  <app-search-select
                    class="sm:col-span-2"
                    formControlName="invoiceId"
                    [search]="searchOpenInvoices"
                    [resolve]="resolveInvoice"
                    [labelFn]="invoiceLabel"
                    [detailFn]="invoiceDetail"
                    [refreshKey]="customerKey()"
                    [placeholder]="form.controls.customerId.value ? 'Facture ouverte du client…' : 'Choisissez d’abord un client'"
                    emptyLabel="Aucune facture ouverte"
                    (itemSelected)="onInvoicePicked(i, $event)"
                  />
                  <input type="number" step="0.01" formControlName="amount" placeholder="Montant" class="rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
                </div>
              }
            </div>
          </div>

          <button type="submit" class="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500 disabled:opacity-50" [disabled]="form.invalid || saving()">
            Enregistrer
          </button>
        </form>
      </section>

      <section class="space-y-4">
        <h2 class="text-lg font-semibold text-white">Historique</h2>
        @if (listError()) {
          <app-error-banner [message]="listError()!" />
        }
        @if (loading() && !payments().length) {
          <app-loading-state />
        } @else if (!payments().length) {
          <app-empty-state title="Aucun paiement." />
        } @else {
          <div class="overflow-hidden rounded-xl border border-slate-800">
            <table class="min-w-full divide-y divide-slate-800 text-sm">
              <thead class="bg-slate-900/80 text-left text-xs uppercase text-slate-500">
                <tr>
                  <th class="px-4 py-3">Date</th>
                  <th class="px-4 py-3">Montant</th>
                  <th class="px-4 py-3">Méthode</th>
                  <th class="px-4 py-3">Affectations</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-800">
                @for (p of payments(); track p.id) {
                  <tr>
                    <td class="px-4 py-3 text-slate-300">{{ p.paymentDate | date: 'shortDate' }}</td>
                    <td class="px-4 py-3 text-white">{{ money(p.amount) }}</td>
                    <td class="px-4 py-3 text-slate-400">{{ p.method }}</td>
                    <td class="px-4 py-3 text-slate-400">{{ p.allocations.length }} facture(s)</td>
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
      </section>
    </div>
  `,
})
export class PaymentsPageComponent {
  private readonly api = inject(PaymentsApiService);
  private readonly customersApi = inject(CustomersApiService);
  private readonly invoicesApi = inject(InvoicesApiService);
  readonly workspaces = inject(WorkspaceService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly listError = signal<string | null>(null);
  readonly formError = signal<string | null>(null);
  readonly payments = signal<Payment[]>([]);
  readonly page = new PageState();

  readonly customerPicker = customerPickerSource(this.customersApi, this.workspaces);

  /** Factures ouvertes (reste à payer > 0) du client sélectionné. */
  readonly searchOpenInvoices = (q: string): Observable<Invoice[]> => {
    const customerId = this.form.controls.customerId.value;
    if (!customerId) {
      return of([]);
    }
    return this.invoicesApi
      .list(this.workspaces.requireSelectedId(), {
        customerId,
        openOnly: true,
        q: q || undefined,
        size: SEARCH_SELECT_LIMIT,
        sort: 'issueDate,desc',
      })
      .pipe(map((page) => page.content));
  };
  readonly resolveInvoice = (id: string): Observable<Invoice> => this.invoicesApi.get(id);
  readonly invoiceLabel = (inv: Invoice): string => inv.number ?? 'Brouillon';
  readonly invoiceDetail = (inv: Invoice): string => `${formatMoney(inv.totalTtc - inv.amountPaid)} restant`;

  readonly form = this.fb.nonNullable.group({
    customerId: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.0001)]],
    paymentDate: [new Date().toISOString().slice(0, 10), Validators.required],
    method: ['TRANSFER' as PaymentMethod],
    reference: [''],
    notes: [''],
    allocations: this.fb.array([this.allocationGroup()]),
  });

  /** Clé de rafraîchissement des sélecteurs de factures : client courant + workspace. */
  private readonly customerIdSignal = toSignal(this.form.controls.customerId.valueChanges, { initialValue: '' });
  readonly customerKey = computed(() => `${this.workspaces.generation()}:${this.customerIdSignal()}`);

  get allocations(): FormArray {
    return this.form.controls.allocations;
  }

  constructor() {
    effect(() => {
      this.workspaces.generation();
      if (!this.workspaces.selectedId()) {
        return;
      }
      this.page.reset();
      this.reloadList();
    });

    // Changement de client : les affectations précédentes ne sont plus valides.
    this.form.controls.customerId.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.allocations.clear();
      this.allocations.push(this.allocationGroup());
    });
  }

  money(v: number): string {
    return formatMoney(v);
  }

  addAllocation(): void {
    this.allocations.push(this.allocationGroup());
  }

  /** Pré-remplit le montant avec le reste à payer de la facture choisie. */
  onInvoicePicked(index: number, invoice: Invoice | null): void {
    if (!invoice) {
      return;
    }
    const ctrl = this.allocations.at(index);
    const remaining = Math.round((invoice.totalTtc - invoice.amountPaid) * 100) / 100;
    if (!Number(ctrl.get('amount')?.value)) {
      ctrl.patchValue({ amount: remaining });
    }
    if (!Number(this.form.controls.amount.value)) {
      this.form.controls.amount.setValue(remaining);
    }
  }

  onPageChange(index: number): void {
    this.page.goTo(index);
    this.reloadList();
  }

  onPageSizeChange(size: number): void {
    this.page.setSize(size);
    this.reloadList();
  }

  submit(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    const raw = this.form.getRawValue();
    const payload: PaymentPayload = {
      workspaceId: this.workspaces.requireSelectedId(),
      customerId: raw.customerId,
      amount: Number(raw.amount),
      paymentDate: raw.paymentDate,
      method: raw.method,
      reference: raw.reference || null,
      notes: raw.notes || null,
      allocations: raw.allocations.map((a) => ({
        invoiceId: a.invoiceId,
        amount: Number(a.amount),
      })),
    };
    this.saving.set(true);
    this.formError.set(null);
    this.api.record(payload).subscribe({
      next: () => {
        this.form.reset({
          customerId: '',
          amount: 0,
          paymentDate: new Date().toISOString().slice(0, 10),
          method: 'TRANSFER',
          reference: '',
          notes: '',
        });
        this.allocations.clear();
        this.allocations.push(this.allocationGroup());
        this.reloadList();
        this.saving.set(false);
      },
      error: (err) => {
        this.formError.set(apiErrorMessage(err, 'Enregistrement impossible'));
        this.saving.set(false);
      },
    });
  }

  private reloadList(): void {
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
        this.payments.set(page.content);
        this.page.apply(page);
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

  private allocationGroup() {
    return this.fb.nonNullable.group({
      invoiceId: ['', Validators.required],
      amount: [0, [Validators.required, Validators.min(0.0001)]],
    });
  }
}
