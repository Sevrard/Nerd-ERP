import { Component, inject, OnInit, signal } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { CustomersApiService } from '../customers/customers-api.service';
import { Customer, customerDisplayName } from '../../shared/models/customer.model';
import { Quotation, QuotationPayload } from '../../shared/models/quotation.model';
import { formatMoney, lineTotals } from '../../shared/utils/money.util';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { QuotationsApiService } from './quotations-api.service';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

@Component({
  selector: 'app-quotations-editor-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ErrorBannerComponent, LoadingStateComponent],
  template: `
    <div class="space-y-6">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <a routerLink="/quotations" class="text-sm text-slate-400 hover:text-white">← Devis</a>
          <h1 class="mt-1 text-2xl font-bold text-white">{{ quotation()?.number ?? 'Nouveau devis' }}</h1>
          @if (quotation()) {
            <p class="text-sm text-slate-400">Statut : {{ quotation()!.status }}</p>
          }
        </div>
        <div class="flex flex-wrap gap-2">
          @if (quotation()?.status === 'DRAFT' || !quotationId) {
            <button
              type="button"
              class="rounded-lg bg-brand-600 px-4 py-2 text-sm text-white hover:bg-brand-500 disabled:opacity-50"
              [disabled]="form.invalid || saving()"
              (click)="saveDraft()"
            >
              Enregistrer brouillon
            </button>
          }
          @if (quotation()?.status === 'DRAFT') {
            <button
              type="button"
              class="rounded-lg border border-slate-600 px-4 py-2 text-sm text-white hover:bg-slate-800 disabled:opacity-50"
              [disabled]="actionBusy()"
              (click)="runAction('send')"
            >
              Envoyer
            </button>
          }
          @if (quotation()?.status === 'SENT') {
            <button
              type="button"
              class="rounded-lg border border-brand-500/50 px-4 py-2 text-sm text-brand-300 hover:bg-brand-950 disabled:opacity-50"
              [disabled]="actionBusy()"
              (click)="runAction('accept')"
            >
              Accepter
            </button>
          }
          @if (quotation()?.status === 'ACCEPTED') {
            <button
              type="button"
              class="rounded-lg bg-brand-600 px-4 py-2 text-sm text-white hover:bg-brand-500 disabled:opacity-50"
              [disabled]="actionBusy()"
              (click)="runAction('convert')"
            >
              Convertir en facture
            </button>
          }
        </div>
      </div>

      @if (error()) {
        <app-error-banner [message]="error()!" />
      }
      @if (loading()) {
        <app-loading-state />
      } @else {
        <form class="space-y-6" [formGroup]="form">
          <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <div>
              <label class="mb-1 block text-xs text-slate-500">Client</label>
              <select
                formControlName="customerId"
                class="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white"
                [disabled]="!!quotation() && quotation()!.status !== 'DRAFT'"
              >
                <option value="">— Choisir —</option>
                @for (c of customers(); track c.id) {
                  <option [value]="c.id">{{ customerLabel(c) }}</option>
                }
              </select>
            </div>
            <div>
              <label class="mb-1 block text-xs text-slate-500">Date d’émission</label>
              <input type="date" formControlName="issueDate" class="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white" />
            </div>
            <div>
              <label class="mb-1 block text-xs text-slate-500">Valide jusqu’au</label>
              <input type="date" formControlName="validUntil" class="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white" />
            </div>
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">Notes</label>
            <textarea formControlName="notes" rows="2" class="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white"></textarea>
          </div>

          <div class="rounded-xl border border-slate-800 overflow-hidden">
            <div class="flex items-center justify-between bg-slate-900/80 px-4 py-3">
              <h2 class="text-sm font-semibold text-white">Lignes</h2>
              @if (!quotation() || quotation()!.status === 'DRAFT') {
                <button type="button" class="text-sm text-brand-400 hover:text-brand-300" (click)="addLine()">+ Ligne</button>
              }
            </div>
            <div class="divide-y divide-slate-800" formArrayName="lines">
              @for (line of lines.controls; track $index; let i = $index) {
                <div class="grid gap-3 p-4 sm:grid-cols-12" [formGroupName]="i">
                  <div class="sm:col-span-4">
                    <input placeholder="Description" formControlName="description" class="w-full rounded border border-slate-700 bg-slate-950 px-2 py-1.5 text-sm text-white" />
                  </div>
                  <div class="sm:col-span-2">
                    <input type="number" step="0.01" placeholder="Qté" formControlName="quantity" class="w-full rounded border border-slate-700 bg-slate-950 px-2 py-1.5 text-sm text-white" />
                  </div>
                  <div class="sm:col-span-2">
                    <input type="number" step="0.01" placeholder="PU HT" formControlName="unitPriceHt" class="w-full rounded border border-slate-700 bg-slate-950 px-2 py-1.5 text-sm text-white" />
                  </div>
                  <div class="sm:col-span-2">
                    <input type="number" step="0.01" placeholder="TVA %" formControlName="vatRate" class="w-full rounded border border-slate-700 bg-slate-950 px-2 py-1.5 text-sm text-white" />
                  </div>
                  <div class="flex items-center justify-between sm:col-span-2">
                    <span class="text-sm text-slate-400">{{ lineTtc(i) }}</span>
                    @if (lines.length > 1 && (!quotation() || quotation()!.status === 'DRAFT')) {
                      <button type="button" class="text-xs text-red-400" (click)="removeLine(i)">Suppr.</button>
                    }
                  </div>
                </div>
              }
            </div>
            <div class="border-t border-slate-800 bg-slate-900/50 px-4 py-3 text-right text-sm text-slate-300">
              Total HT {{ displayTotals().ht }} · TVA {{ displayTotals().vat }} · TTC
              <span class="font-semibold text-white">{{ displayTotals().ttc }}</span>
            </div>
          </div>
        </form>
      }
    </div>
  `,
})
export class QuotationsEditorPageComponent implements OnInit {
  private readonly api = inject(QuotationsApiService);
  private readonly customersApi = inject(CustomersApiService);
  private readonly workspaces = inject(WorkspaceService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  quotationId: string | null = null;
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly actionBusy = signal(false);
  readonly error = signal<string | null>(null);
  readonly quotation = signal<Quotation | null>(null);
  readonly customers = signal<Customer[]>([]);

  readonly form = this.fb.nonNullable.group({
    customerId: ['', Validators.required],
    issueDate: [''],
    validUntil: [''],
    notes: [''],
    lines: this.fb.array([this.newLineGroup()]),
  });

  get lines(): FormArray {
    return this.form.controls.lines;
  }

  ngOnInit(): void {
    const workspaceId = this.workspaces.selectedId();
    if (workspaceId) {
      this.customersApi.search(workspaceId, {}).subscribe((page) => this.customers.set(page.content));
    }
    this.quotationId = this.route.snapshot.paramMap.get('id');
    if (this.quotationId && this.route.snapshot.url.some((s) => s.path !== 'new')) {
      this.loadQuotation(this.quotationId);
    } else {
      this.quotationId = null;
    }
  }

  customerLabel(c: Customer): string {
    return customerDisplayName(c);
  }

  displayTotals(): { ht: string; vat: string; ttc: string } {
    const raw = this.lines.getRawValue();
    let ht = 0;
    let vat = 0;
    let ttc = 0;
    for (const l of raw) {
      const t = lineTotals(Number(l.quantity), Number(l.unitPriceHt), Number(l.vatRate));
      ht += t.ht;
      vat += t.vat;
      ttc += t.ttc;
    }
    return { ht: formatMoney(ht), vat: formatMoney(vat), ttc: formatMoney(ttc) };
  }

  lineTtc(index: number): string {
    const g = this.lines.at(index).getRawValue();
    const t = lineTotals(Number(g.quantity), Number(g.unitPriceHt), Number(g.vatRate));
    return formatMoney(t.ttc);
  }

  addLine(): void {
    this.lines.push(this.newLineGroup());
  }

  removeLine(i: number): void {
    this.lines.removeAt(i);
  }

  saveDraft(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    const payload = this.buildPayload();
    this.saving.set(true);
    this.error.set(null);
    const req = this.quotationId ? this.api.update(this.quotationId, payload) : this.api.create(payload);
    req.subscribe({
      next: (q) => {
        this.quotation.set(q);
        if (!this.quotationId) {
          void this.router.navigate(['/quotations', q.id]);
        }
        this.quotationId = q.id;
        this.saving.set(false);
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Enregistrement impossible'));
        this.saving.set(false);
      },
    });
  }

  runAction(kind: 'send' | 'accept' | 'convert'): void {
    if (!this.quotationId || this.actionBusy()) {
      return;
    }
    this.actionBusy.set(true);
    this.error.set(null);
    const id = this.quotationId;
    const done = () => this.actionBusy.set(false);
    if (kind === 'send') {
      this.api.send(id).subscribe({
        next: (q) => {
          this.quotation.set(q);
          done();
        },
        error: (err) => {
          this.error.set(apiErrorMessage(err, 'Envoi impossible'));
          done();
        },
      });
      return;
    }
    if (kind === 'accept') {
      this.api.accept(id).subscribe({
        next: (q) => {
          this.quotation.set(q);
          done();
        },
        error: (err) => {
          this.error.set(apiErrorMessage(err, 'Acceptation impossible'));
          done();
        },
      });
      return;
    }
    this.api.convertToInvoice(id).subscribe({
      next: (inv) => {
        done();
        void this.router.navigate(['/invoices', inv.id]);
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Conversion impossible'));
        this.loadQuotation(id);
        done();
      },
    });
  }

  private loadQuotation(id: string): void {
    this.loading.set(true);
    this.api.get(id).subscribe({
      next: (q) => {
        this.quotation.set(q);
        this.patchForm(q);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Devis introuvable'));
        this.loading.set(false);
      },
    });
  }

  private patchForm(q: Quotation): void {
    this.lines.clear();
    const source = q.lines.length
      ? q.lines
      : [{ productId: null as string | null, description: '', quantity: 1, unitPriceHt: 0, vatRate: 20 }];
    for (const line of source) {
      this.lines.push(
        this.fb.nonNullable.group({
          productId: [line.productId ?? ''],
          description: [line.description, Validators.required],
          quantity: [line.quantity, [Validators.required, Validators.min(0.0001)]],
          unitPriceHt: [line.unitPriceHt, [Validators.required, Validators.min(0)]],
          vatRate: [line.vatRate, [Validators.required, Validators.min(0)]],
        }),
      );
    }
    this.form.patchValue({
      customerId: q.customerId,
      issueDate: q.issueDate ?? '',
      validUntil: q.validUntil ?? '',
      notes: q.notes ?? '',
    });
    if (q.status !== 'DRAFT') {
      this.form.disable();
    }
  }

  private buildPayload(): QuotationPayload {
    return {
      workspaceId: this.workspaces.requireSelectedId(),
      customerId: this.form.value.customerId!,
      issueDate: this.form.value.issueDate || null,
      validUntil: this.form.value.validUntil || null,
      notes: this.form.value.notes || null,
      lines: this.lines.getRawValue().map((l) => ({
        productId: l.productId || null,
        description: l.description,
        quantity: Number(l.quantity),
        unitPriceHt: Number(l.unitPriceHt),
        vatRate: Number(l.vatRate),
      })),
    };
  }

  private newLineGroup() {
    return this.fb.nonNullable.group({
      productId: [''],
      description: ['', Validators.required],
      quantity: [1, [Validators.required, Validators.min(0.0001)]],
      unitPriceHt: [0, [Validators.required, Validators.min(0)]],
      vatRate: [20, [Validators.required, Validators.min(0)]],
    });
  }
}
