import { Component, effect, inject, output, signal } from '@angular/core';
import { InvoiceScanApiService } from './invoice-scan-api.service';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { CustomersApiService } from '../customers/customers-api.service';
import { MerchantsApiService } from '../merchants/merchants-api.service';
import { Merchant } from '../../shared/models/merchant.model';
import {
  InvoiceScanImportError,
  InvoiceScanPageResult,
  InvoiceScanPendingRow,
  InvoiceScanPreview,
  scanCustomerLabel,
} from '../../shared/models/invoice-scan.model';
import { Customer } from '../../shared/models/customer.model';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { InvoiceScanReviewModalComponent } from './invoice-scan-review-modal.component';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

type Step = 'idle' | 'analyzing' | 'review-modal' | 'batch-done';

const CREATE_CLIENT_VALUE = '__create__';

@Component({
  selector: 'app-invoice-scan-panel',
  standalone: true,
  imports: [ErrorBannerComponent, InvoiceScanReviewModalComponent],
  template: `
    <section class="rounded-xl border border-slate-800 bg-slate-900/80 p-5">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 class="text-lg font-semibold text-white">Scan intelligent de factures PDF</h2>
          <p class="mt-1 text-sm text-slate-400">
            PDF multi-pages : une facture par page, révision avant enregistrement (max. 50 pages).
          </p>
        </div>
      </div>

      @if (error()) {
        <div class="mt-4">
          <app-error-banner [message]="error()!" />
        </div>
      }

      <div class="mt-4 flex flex-wrap items-end gap-4">
        <label class="block min-w-[220px] text-sm">
          <span class="mb-1.5 block text-slate-400">Marchand (import PDF)</span>
          <select
            class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white disabled:opacity-50"
            [disabled]="!merchants().length || step() === 'analyzing' || step() === 'review-modal'"
            [value]="selectedMerchantId() ?? ''"
            (change)="onScanMerchantChange($event)"
          >
            @if (!merchants().length) {
              <option value="">Aucun marchand pour ce workspace</option>
            } @else {
              @for (m of merchants(); track m.id) {
                <option [value]="m.id">{{ m.name }}</option>
              }
            }
          </select>
        </label>
        @if (selectedMerchant(); as m) {
          @if (m.logoUrl) {
            <img [src]="m.logoUrl" [alt]="m.name" class="mb-0.5 h-9 w-9 rounded-sm object-cover" width="36" height="36" />
          }
        }
      </div>

      @if (step() === 'batch-done') {
        <div class="mt-4 rounded-lg border border-brand-500/30 bg-brand-500/10 p-4 text-sm text-brand-100">
          <p class="font-medium">Import terminé</p>
          <ul class="mt-2 list-inside list-disc text-brand-100/90">
            <li>{{ batchImported() }} facture(s) enregistrée(s)</li>
            <li>{{ batchPayments() }} paiement(s) (doublons)</li>
            @if (batchAnalyzeErrors() > 0) {
              <li>{{ batchAnalyzeErrors() }} page(s) en erreur à l’analyse</li>
            }
            @if (batchSkipped() > 0) {
              <li>{{ batchSkipped() }} ligne(s) retirée(s) sans enregistrement</li>
            }
          </ul>
          @if (batchErrorDetails().length > 0) {
            <ul class="mt-3 max-h-40 space-y-1 overflow-y-auto text-xs text-red-200/90">
              @for (item of batchErrorDetails(); track $index) {
                <li>Page {{ item.pageNumber }} — {{ item.message }}</li>
              }
            </ul>
          }
          <button type="button" class="mt-3 text-slate-300 underline hover:text-white" (click)="reset()">
            Nouvel import
          </button>
        </div>
      }

      <div
        class="mt-4 flex min-h-[140px] cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed border-slate-700 bg-slate-950/50 px-4 py-8 text-center transition hover:border-brand-500/60"
        [class.pointer-events-none]="step() === 'analyzing' || step() === 'review-modal'"
        [class.opacity-70]="step() === 'analyzing' || step() === 'review-modal'"
        (click)="fileInput.click()"
        (dragover)="onDragOver($event)"
        (drop)="onDrop($event)"
      >
        @if (step() === 'analyzing') {
          <div
            class="mb-3 h-8 w-8 animate-spin rounded-full border-2 border-brand-500 border-t-transparent"
            aria-hidden="true"
          ></div>
          <p class="text-sm font-medium text-white">Analyse IA en cours…</p>
          <p class="mt-1 text-xs text-slate-500">Découpage des pages et analyse Gemini…</p>
        } @else {
          <p class="text-sm text-slate-300">Glissez-déposez un PDF ou cliquez pour parcourir</p>
          <p class="mt-1 text-xs text-slate-500">Max. 10 Mo, 50 pages — une facture par page</p>
        }
      </div>
      <input
        #fileInput
        type="file"
        accept="application/pdf,.pdf"
        class="hidden"
        (change)="onFileSelected($event)"
      />
    </section>

    @if (step() === 'review-modal') {
      <app-invoice-scan-review-modal
        [pendingRows]="pendingRows()"
        [customers]="customers()"
        [batchPageCount]="batchPageCount()"
        [batchErrorDetails]="batchErrorDetails()"
        [createClientValue]="CREATE_CLIENT_VALUE"
        (closed)="closeReview()"
        (rowPatch)="patchRow($event.scanId, $event.patch)"
        (saveRow)="saveRow($event)"
        (removeRow)="removeRow($event)"
      />
    }
  `,
})
export class InvoiceScanPanelComponent {
  readonly invoicesChanged = output<void>();

  private readonly api = inject(InvoiceScanApiService);
  private readonly workspaces = inject(WorkspaceService);
  private readonly customersApi = inject(CustomersApiService);
  private readonly merchantsApi = inject(MerchantsApiService);

  readonly CREATE_CLIENT_VALUE = CREATE_CLIENT_VALUE;

  readonly step = signal<Step>('idle');
  readonly error = signal<string | null>(null);
  readonly pendingRows = signal<InvoiceScanPendingRow[]>([]);
  readonly customers = signal<Customer[]>([]);
  readonly merchants = signal<Merchant[]>([]);
  readonly selectedMerchantId = signal<string | null>(null);

  readonly batchPageCount = signal(0);
  readonly batchImported = signal(0);
  readonly batchPayments = signal(0);
  readonly batchAnalyzeErrors = signal(0);
  readonly batchSkipped = signal(0);
  readonly batchErrorDetails = signal<InvoiceScanImportError[]>([]);

  constructor() {
    effect(() => {
      const workspaceId = this.workspaces.selectedId();
      const gen = this.workspaces.generation();
      if (!workspaceId) {
        this.merchants.set([]);
        this.selectedMerchantId.set(null);
        return;
      }
      this.merchantsApi.list(workspaceId).subscribe({
        next: (list) => {
          if (this.workspaces.isStale(gen)) {
            return;
          }
          this.merchants.set(list);
          const current = this.selectedMerchantId();
          const stillValid = current && list.some((m) => m.id === current);
          if (!stillValid) {
            const etsy = list.find((m) => m.slug === 'etsy');
            this.selectedMerchantId.set(etsy?.id ?? list[0]?.id ?? null);
          }
        },
        error: () => {
          if (this.workspaces.isStale(gen)) {
            return;
          }
          this.merchants.set([]);
          this.selectedMerchantId.set(null);
        },
      });
    });
  }

  selectedMerchant(): Merchant | null {
    const id = this.selectedMerchantId();
    if (!id) {
      return null;
    }
    return this.merchants().find((m) => m.id === id) ?? null;
  }

  onScanMerchantChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.selectedMerchantId.set(value || null);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    const file = event.dataTransfer?.files?.[0];
    if (file) {
      this.upload(file);
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.upload(file);
    }
    input.value = '';
  }

  upload(file: File): void {
    const workspaceId = this.workspaces.selectedId();
    if (!workspaceId) {
      this.error.set('Sélectionnez un workspace avant d’importer un PDF.');
      return;
    }
    if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
      this.error.set('Format non supporté : PDF uniquement.');
      return;
    }
    const merchantId = this.selectedMerchantId();
    if (this.merchants().length > 0 && !merchantId) {
      this.error.set('Sélectionnez un marchand avant d’importer.');
      return;
    }
    this.error.set(null);
    this.step.set('analyzing');
    this.pendingRows.set([]);
    this.batchPageCount.set(0);
    this.batchImported.set(0);
    this.batchPayments.set(0);
    this.batchAnalyzeErrors.set(0);
    this.batchSkipped.set(0);
    this.batchErrorDetails.set([]);

    this.api.analyze(workspaceId, file, merchantId ?? undefined).subscribe({
      next: (batch) => {
        this.batchPageCount.set(batch.pageCount);
        const analyzeErrors: InvoiceScanImportError[] = batch.pages
          .filter((p) => p.errorMessage)
          .map((p) => ({
            pageNumber: p.pageNumber,
            phase: 'analyze' as const,
            message: p.errorMessage!,
          }));
        this.batchAnalyzeErrors.set(analyzeErrors.length);
        this.batchErrorDetails.set(analyzeErrors);

        const rows = batch.pages
          .filter((p): p is InvoiceScanPageResult & { preview: InvoiceScanPreview } => !!p.preview)
          .map((p) => this.toPendingRow(p.pageNumber, p.preview));

        this.pendingRows.set(rows);
        this.loadCustomers(workspaceId);
        this.step.set('review-modal');
      },
      error: (err) => {
        this.step.set('idle');
        this.error.set(apiErrorMessage(err, 'Échec de l’analyse du PDF'));
      },
    });
  }

  patchRow(scanId: string, patch: Partial<InvoiceScanPendingRow>): void {
    this.pendingRows.update((rows) =>
      rows.map((r) => {
        if (r.scanId !== scanId) {
          return r;
        }
        const next = { ...r, ...patch, rowError: null };
        if (patch.orderNumber !== undefined || patch.merchant !== undefined || patch.invoiceNumber !== undefined) {
          next.duplicateDetected = false;
          next.duplicateRef = null;
        }
        return next;
      }),
    );
  }

  removeRow(scanId: string): void {
    this.pendingRows.update((rows) => rows.filter((r) => r.scanId !== scanId));
    this.batchSkipped.update((n) => n + 1);
    if (!this.pendingRows().length) {
      this.closeReview();
    }
  }

  saveRow(scanId: string): void {
    const row = this.pendingRows().find((r) => r.scanId === scanId);
    const workspaceId = this.workspaces.selectedId();
    if (!row || !workspaceId) {
      return;
    }
    if (row.totalTtc <= 0) {
      this.patchRow(scanId, { rowError: 'Montant TTC invalide' });
      return;
    }

    const createClient = row.customerSelection === CREATE_CLIENT_VALUE;
    const duplicate = row.duplicateDetected;

    this.patchRow(scanId, { saving: true, rowError: null });

    this.api
      .commit({
        scanId: row.scanId,
        workspaceId,
        createCustomerConfirmed: createClient,
        invoiceConfirmed: true,
        recordDuplicateAsPayment: duplicate,
        duplicatePaymentConfirmed: duplicate,
        merchant: row.merchant.trim() || null,
        customerId: createClient ? null : row.customerSelection,
        orderNumber: row.orderNumber.trim() || null,
        invoiceNumber: row.invoiceNumber.trim() || null,
        issueDate: row.issueDate || null,
        totalTtc: row.totalTtc,
      })
      .subscribe({
        next: () => {
          if (duplicate) {
            this.batchPayments.update((n) => n + 1);
          } else {
            this.batchImported.update((n) => n + 1);
          }
          this.invoicesChanged.emit();
          this.pendingRows.update((rows) => rows.filter((r) => r.scanId !== scanId));
          if (!this.pendingRows().length) {
            this.closeReview();
          }
        },
        error: (err) => {
          const message = apiErrorMessage(err, 'Enregistrement impossible');
          this.patchRow(scanId, { saving: false, rowError: message });
          this.batchErrorDetails.update((list) => [
            ...list,
            { pageNumber: row.pageNumber, phase: 'commit', message },
          ]);
        },
      });
  }

  closeReview(): void {
    this.step.set('batch-done');
  }

  reset(): void {
    this.step.set('idle');
    this.error.set(null);
    this.pendingRows.set([]);
    this.customers.set([]);
    this.batchPageCount.set(0);
    this.batchImported.set(0);
    this.batchPayments.set(0);
    this.batchAnalyzeErrors.set(0);
    this.batchSkipped.set(0);
    this.batchErrorDetails.set([]);
  }

  private loadCustomers(workspaceId: string): void {
    this.customersApi.search(workspaceId, { page: 0, size: 500 }).subscribe({
      next: (page) => this.customers.set(page.content),
      error: () => this.customers.set([]),
    });
  }

  private toPendingRow(pageNumber: number, preview: InvoiceScanPreview): InvoiceScanPendingRow {
    const ext = preview.extraction;
    const totalTtc = ext.totalTtc ?? this.estimateTtc(ext);
    const customerSelection = preview.matchedCustomerId ?? CREATE_CLIENT_VALUE;

    return {
      scanId: preview.scanId,
      pageNumber,
      merchant: this.resolveRowMerchant(ext.merchant),
      orderNumber: ext.orderNumber ?? '',
      invoiceNumber: ext.invoiceNumber ?? '',
      issueDate: ext.issueDate ?? new Date().toISOString().slice(0, 10),
      totalTtc,
      customerSelection,
      duplicateDetected: !!preview.duplicate?.detected,
      duplicateRef: preview.duplicate?.externalReference ?? null,
      extractedClientLabel: scanCustomerLabel(ext.customer),
      saving: false,
      rowError: null,
    };
  }

  private resolveRowMerchant(extracted: string | null | undefined): string {
    const fromPdf = extracted?.trim();
    if (fromPdf) {
      return fromPdf;
    }
    return this.selectedMerchant()?.name ?? '';
  }

  private estimateTtc(ext: InvoiceScanPreview['extraction']): number {
    let sum = 0;
    for (const line of ext.lines) {
      const vat = line.vatRate <= 1 ? line.vatRate * 100 : line.vatRate;
      const ht = line.quantity * line.unitPriceHt;
      sum += ht * (1 + vat / 100);
    }
    if (ext.shippingAmountHt) {
      const sv = ext.shippingVatRate ?? 20;
      const vat = sv <= 1 ? sv * 100 : sv;
      sum += ext.shippingAmountHt * (1 + vat / 100);
    }
    return Math.round(sum * 100) / 100;
  }
}
