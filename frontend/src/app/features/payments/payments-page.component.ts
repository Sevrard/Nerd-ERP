import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { CustomersApiService } from '../customers/customers-api.service';
import { InvoicesApiService } from '../invoices/invoices-api.service';
import { Customer, customerDisplayName } from '../../shared/models/customer.model';
import { Invoice } from '../../shared/models/invoice.model';
import { Payment, PaymentMethod, PaymentPayload } from '../../shared/models/payment.model';
import { formatMoney } from '../../shared/utils/money.util';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { PaymentsApiService } from './payments-api.service';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

@Component({
  selector: 'app-payments-page',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe, LoadingStateComponent, EmptyStateComponent, ErrorBannerComponent],
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
              <select formControlName="customerId" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white">
                <option value="">— Choisir —</option>
                @for (c of customers(); track c.id) {
                  <option [value]="c.id">{{ label(c) }}</option>
                }
              </select>
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
                  <select formControlName="invoiceId" class="rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white sm:col-span-2">
                    <option value="">Facture</option>
                    @for (inv of openInvoices(); track inv.id) {
                      <option [value]="inv.id">{{ inv.number }} · {{ money(inv.totalTtc - inv.amountPaid) }} restant</option>
                    }
                  </select>
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
        @if (loading()) {
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
  private readonly workspaces = inject(WorkspaceService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly listError = signal<string | null>(null);
  readonly formError = signal<string | null>(null);
  readonly payments = signal<Payment[]>([]);
  readonly customers = signal<Customer[]>([]);
  readonly invoices = signal<Invoice[]>([]);

  readonly openInvoices = signal<Invoice[]>([]);

  readonly form = this.fb.nonNullable.group({
    customerId: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.0001)]],
    paymentDate: [new Date().toISOString().slice(0, 10), Validators.required],
    method: ['TRANSFER' as PaymentMethod],
    reference: [''],
    notes: [''],
    allocations: this.fb.array([this.allocationGroup()]),
  });

  get allocations(): FormArray {
    return this.form.controls.allocations;
  }

  constructor() {
    effect(() => {
      const gen = this.workspaces.generation();
      const workspaceId = this.workspaces.selectedId();
      if (!workspaceId) {
        return;
      }
      this.customersApi.search(workspaceId, {}).subscribe((page) => this.customers.set(page.content));
      this.invoicesApi.list(workspaceId).subscribe((page) => {
        if (this.workspaces.isStale(gen)) {
          return;
        }
        this.invoices.set(page.content);
        this.refreshOpenInvoices();
      });
      this.reloadList();
    });

    this.form.controls.customerId.valueChanges.subscribe(() => this.refreshOpenInvoices());
  }

  label(c: Customer): string {
    return customerDisplayName(c);
  }

  money(v: number): string {
    return formatMoney(v);
  }

  addAllocation(): void {
    this.allocations.push(this.allocationGroup());
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
    this.api.list(workspaceId).subscribe({
      next: (page) => {
        if (this.workspaces.isStale(gen)) {
          return;
        }
        this.payments.set(page.content);
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

  private refreshOpenInvoices(): void {
    const customerId = this.form.controls.customerId.value;
    const open = this.invoices().filter(
      (i) => i.customerId === customerId && i.totalTtc - i.amountPaid > 0.0001,
    );
    this.openInvoices.set(open);
  }

  private allocationGroup() {
    return this.fb.nonNullable.group({
      invoiceId: ['', Validators.required],
      amount: [0, [Validators.required, Validators.min(0.0001)]],
    });
  }
}
