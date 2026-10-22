import { Component, input, output } from '@angular/core';
import { Customer } from '../../shared/models/customer.model';
import { InvoiceScanImportError, InvoiceScanPendingRow } from '../../shared/models/invoice-scan.model';
import { IconActionComponent } from '../../shared/ui/icon-action.component';
import { formatMoney } from '../../shared/utils/money.util';

export interface InvoiceScanRowPatchEvent {
  scanId: string;
  patch: Partial<InvoiceScanPendingRow>;
}

@Component({
  selector: 'app-invoice-scan-review-modal',
  standalone: true,
  imports: [IconActionComponent],
  template: `
    <div class="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 p-2 sm:p-4" role="dialog" aria-modal="true">
      <div
        class="flex h-[94vh] w-[min(96vw,1400px)] flex-col overflow-hidden rounded-xl border border-slate-700 bg-slate-900 shadow-2xl"
      >
        <header class="shrink-0 border-b border-slate-800 px-6 py-5">
          <h3 class="text-2xl font-semibold text-white">Factures détectées — révision avant enregistrement</h3>
          <p class="mt-1 text-sm text-slate-400">
            {{ pendingRows().length }} facture(s) à valider · {{ batchPageCount() }} page(s) analysée(s)
          </p>
          @if (batchErrorDetails().length > 0) {
            <div class="mt-3 rounded-lg border border-red-500/30 bg-red-950/30 px-3 py-2 text-sm text-red-100">
              <p class="font-medium">Erreurs d’analyse (pages ignorées)</p>
              <ul class="mt-1 list-inside list-disc text-xs text-red-200/90">
                @for (item of batchErrorDetails(); track $index) {
                  <li>Page {{ item.pageNumber }} : {{ item.message }}</li>
                }
              </ul>
            </div>
          }
        </header>

        <div class="min-h-0 flex-1 overflow-auto px-6 py-5">
          @if (!pendingRows().length) {
            <p class="text-center text-base text-slate-400">Toutes les factures ont été traitées.</p>
          } @else {
            <div class="overflow-x-auto rounded-lg border border-slate-800">
              <table class="min-w-full text-left text-base">
                <thead class="bg-slate-950/90 text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th class="px-3 py-3">Page</th>
                    <th class="px-3 py-3">Marchand</th>
                    <th class="px-3 py-3">N° commande</th>
                    <th class="px-3 py-3">Réf. reçu</th>
                    <th class="px-3 py-3">Client</th>
                    <th class="px-3 py-3">Date</th>
                    <th class="px-3 py-3">Montant TTC</th>
                    <th class="px-3 py-3">Info</th>
                    <th class="px-3 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-800">
                  @for (row of pendingRows(); track row.scanId) {
                    <tr class="align-top hover:bg-slate-900/40">
                      <td class="px-3 py-3 text-slate-400">{{ row.pageNumber }}</td>
                      <td class="px-3 py-3">
                        <input
                          type="text"
                          class="w-28 rounded border border-slate-700 bg-slate-950 px-2 py-1.5 text-white"
                          [value]="row.merchant"
                          (input)="emitPatch(row.scanId, { merchant: inputValue($event) })"
                        />
                      </td>
                      <td class="px-3 py-3">
                        <input
                          type="text"
                          class="w-32 rounded border border-slate-700 bg-slate-950 px-2 py-1.5 text-white"
                          [value]="row.orderNumber"
                          (input)="emitPatch(row.scanId, { orderNumber: inputValue($event) })"
                        />
                      </td>
                      <td class="px-3 py-3">
                        <input
                          type="text"
                          class="w-28 rounded border border-slate-700 bg-slate-950 px-2 py-1.5 text-white"
                          [value]="row.invoiceNumber"
                          (input)="emitPatch(row.scanId, { invoiceNumber: inputValue($event) })"
                        />
                      </td>
                      <td class="px-3 py-3">
                        <select
                          class="max-w-[220px] rounded border border-slate-700 bg-slate-950 px-2 py-1.5 text-white"
                          [value]="row.customerSelection"
                          (change)="emitPatch(row.scanId, { customerSelection: selectValue($event) })"
                        >
                          <option [value]="createClientValue()">
                            + Créer « {{ row.extractedClientLabel }} »
                          </option>
                          @for (c of customers(); track c.id) {
                            <option [value]="c.id">{{ customerOptionLabel(c) }}</option>
                          }
                        </select>
                      </td>
                      <td class="px-3 py-3">
                        <input
                          type="date"
                          class="rounded border border-slate-700 bg-slate-950 px-2 py-1.5 text-white"
                          [value]="row.issueDate"
                          (input)="emitPatch(row.scanId, { issueDate: inputValue($event) })"
                        />
                      </td>
                      <td class="px-3 py-3">
                        <input
                          type="number"
                          step="0.01"
                          min="0"
                          class="w-24 rounded border border-slate-700 bg-slate-950 px-2 py-1.5 text-white"
                          [value]="row.totalTtc"
                          (input)="emitPatch(row.scanId, { totalTtc: numberInput($event) })"
                        />
                      </td>
                      <td class="px-3 py-3 text-xs">
                        @if (row.duplicateDetected) {
                          <span class="text-amber-300">Doublon ({{ row.duplicateRef }}) — enregistrera un paiement</span>
                        } @else {
                          <span class="text-slate-500">Nouvelle facture</span>
                        }
                        @if (row.rowError) {
                          <p class="mt-1 text-red-400">{{ row.rowError }}</p>
                        }
                      </td>
                      <td class="px-3 py-3 text-right whitespace-nowrap">
                        <div class="inline-flex items-center justify-end gap-1">
                          <app-icon-action
                            icon="check"
                            label="Enregistrer la facture"
                            variant="brand"
                            [loading]="row.saving"
                            [disabled]="row.saving"
                            (action)="saveRow.emit(row.scanId)"
                          />
                          <app-icon-action
                            icon="x"
                            label="Retirer de la liste"
                            variant="default"
                            [disabled]="row.saving"
                            (action)="removeRow.emit(row.scanId)"
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

        <footer class="flex shrink-0 justify-end gap-3 border-t border-slate-800 px-6 py-4">
          <button
            type="button"
            class="rounded-lg border border-slate-600 px-4 py-2 text-sm text-slate-300 hover:bg-slate-800"
            (click)="closed.emit()"
          >
            Fermer
          </button>
        </footer>
      </div>
    </div>
  `,
})
export class InvoiceScanReviewModalComponent {
  readonly pendingRows = input.required<InvoiceScanPendingRow[]>();
  readonly customers = input.required<Customer[]>();
  readonly batchPageCount = input.required<number>();
  readonly batchErrorDetails = input.required<InvoiceScanImportError[]>();
  readonly createClientValue = input.required<string>();

  readonly closed = output<void>();
  readonly rowPatch = output<InvoiceScanRowPatchEvent>();
  readonly saveRow = output<string>();
  readonly removeRow = output<string>();

  readonly formatMoney = formatMoney;

  emitPatch(scanId: string, patch: Partial<InvoiceScanPendingRow>): void {
    this.rowPatch.emit({ scanId, patch });
  }

  inputValue(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  selectValue(event: Event): string {
    return (event.target as HTMLSelectElement).value;
  }

  numberInput(event: Event): number {
    const v = parseFloat((event.target as HTMLInputElement).value);
    return Number.isFinite(v) ? v : 0;
  }

  customerOptionLabel(c: Customer): string {
    if (c.companyName?.trim()) {
      return c.companyName.trim();
    }
    const name = [c.firstName, c.lastName].filter(Boolean).join(' ').trim();
    return name || c.email || c.id;
  }
}
