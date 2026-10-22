import { Component, input, output } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { EtsySyncProductDraft, EtsySyncReceiptDraft } from '../../shared/models/etsy-sync.model';
import { formatMoney } from '../../shared/utils/money.util';

@Component({
  selector: 'app-etsy-sync-review-modal',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div
      class="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 p-2 sm:p-4 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      (click)="backdropClose.emit()"
    >
      <div
        class="flex h-[min(94vh,880px)] w-[min(96vw,920px)] flex-col overflow-hidden rounded-2xl border border-slate-700/80 bg-slate-900 shadow-2xl shadow-black/50"
        (click)="$event.stopPropagation()"
      >
        <header class="shrink-0 border-b border-slate-800 px-6 py-5 sm:px-8">
          <div class="flex flex-wrap items-start justify-between gap-4">
            <div>
              <h3 class="text-xl font-semibold tracking-tight text-white sm:text-2xl">Import Etsy — révision</h3>
              <p class="mt-1 text-sm text-slate-400">
                @if (step() === 'products') {
                  Listings à intégrer au catalogue
                } @else {
                  Commandes à importer en factures
                }
              </p>
            </div>
            <button
              type="button"
              class="rounded-lg border border-slate-700 px-3 py-1.5 text-sm text-slate-400 transition hover:border-slate-600 hover:text-white"
              (click)="closed.emit()"
            >
              Fermer
            </button>
          </div>
          <div class="mt-4 flex gap-2 text-xs font-medium uppercase tracking-wide">
            <span
              class="rounded-full px-3 py-1"
              [class.bg-amber-500/20]="step() === 'products'"
              [class.text-amber-200]="step() === 'products'"
              [class.bg-slate-800]="step() !== 'products'"
              [class.text-slate-500]="step() !== 'products'"
            >
              1 · Produits ({{ products().length }})
            </span>
            <span
              class="rounded-full px-3 py-1"
              [class.bg-amber-500/20]="step() === 'receipts'"
              [class.text-amber-200]="step() === 'receipts'"
              [class.bg-slate-800]="step() !== 'receipts'"
              [class.text-slate-500]="step() !== 'receipts'"
            >
              2 · Commandes ({{ receipts().length }})
            </span>
          </div>
        </header>

        <div class="min-h-0 flex-1 overflow-auto px-6 py-6 sm:px-8">
          @if (step() === 'products' && currentProduct(); as p) {
            <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
              <div>
                <p class="text-sm font-medium text-white">
                  Produit {{ productIndex() + 1 }} sur {{ products().length }}
                </p>
                <p class="mt-0.5 text-xs text-slate-500">Listing Etsy #{{ p.listingId }}</p>
              </div>
              @if (p.alreadyInCatalog) {
                <span
                  class="rounded-full border border-emerald-500/40 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-300"
                >
                  Déjà au catalogue
                </span>
              }
            </div>
            <div class="mb-6 h-1.5 overflow-hidden rounded-full bg-slate-800">
              <div
                class="h-full rounded-full bg-amber-500 transition-all duration-300"
                [style.width.%]="productProgressPercent()"
              ></div>
            </div>

            <form class="space-y-5" [formGroup]="productForm()">
              <div class="grid gap-5 lg:grid-cols-2">
                <div class="lg:col-span-1">
                  <label class="mb-1.5 block text-xs font-medium uppercase tracking-wide text-slate-500">Référence</label>
                  <input
                    formControlName="referenceCode"
                    class="w-full rounded-lg border border-slate-700 bg-slate-950/80 px-4 py-2.5 text-sm text-white outline-none ring-amber-500/40 transition focus:border-amber-500/60 focus:ring-2"
                  />
                </div>
                <div class="lg:col-span-2">
                  <label class="mb-1.5 block text-xs font-medium uppercase tracking-wide text-slate-500">Nom du produit</label>
                  <input
                    formControlName="name"
                    class="w-full rounded-lg border border-slate-700 bg-slate-950/80 px-4 py-2.5 text-sm text-white outline-none ring-amber-500/40 transition focus:border-amber-500/60 focus:ring-2"
                  />
                </div>
                <div class="lg:col-span-2">
                  <label class="mb-1.5 block text-xs font-medium uppercase tracking-wide text-slate-500">Description</label>
                  <textarea
                    formControlName="description"
                    rows="8"
                    class="w-full resize-y rounded-lg border border-slate-700 bg-slate-950/80 px-4 py-3 text-sm leading-relaxed text-white outline-none ring-amber-500/40 transition focus:border-amber-500/60 focus:ring-2"
                  ></textarea>
                </div>
                <div>
                  <label class="mb-1.5 block text-xs font-medium uppercase tracking-wide text-slate-500">Prix HT</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    formControlName="unitPriceHt"
                    class="w-full rounded-lg border border-slate-700 bg-slate-950/80 px-4 py-2.5 text-sm text-white outline-none ring-amber-500/40 transition focus:border-amber-500/60 focus:ring-2"
                  />
                </div>
                <div>
                  <label class="mb-1.5 block text-xs font-medium uppercase tracking-wide text-slate-500">TVA %</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    formControlName="vatRate"
                    class="w-full rounded-lg border border-slate-700 bg-slate-950/80 px-4 py-2.5 text-sm text-white outline-none ring-amber-500/40 transition focus:border-amber-500/60 focus:ring-2"
                  />
                </div>
              </div>
              <label class="flex cursor-pointer items-center gap-2.5 text-sm text-slate-300">
                <input
                  type="checkbox"
                  formControlName="active"
                  class="size-4 rounded border-slate-600 bg-slate-950 text-amber-600 focus:ring-amber-500/40"
                />
                Produit actif dans le catalogue
              </label>
            </form>
          }

          @if (step() === 'receipts' && currentReceipt(); as r) {
            <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
              <div>
                <p class="text-sm font-medium text-white">
                  Commande {{ receiptIndex() + 1 }} sur {{ receipts().length }}
                </p>
                <p class="mt-0.5 text-xs text-slate-500">Reçu #{{ r.receiptId }}</p>
              </div>
              @if (r.alreadyImported) {
                <span class="rounded-full border border-slate-600 bg-slate-800 px-3 py-1 text-xs text-slate-400">
                  Déjà importée
                </span>
              }
            </div>
            <div class="mb-6 h-1.5 overflow-hidden rounded-full bg-slate-800">
              <div
                class="h-full rounded-full bg-amber-500 transition-all duration-300"
                [style.width.%]="receiptProgressPercent()"
              ></div>
            </div>

            <div class="rounded-xl border border-slate-800 bg-slate-950/50 p-5 sm:p-6">
              <div class="flex flex-wrap gap-x-8 gap-y-2 text-sm">
                <div>
                  <span class="text-slate-500">Client</span>
                  <p class="font-medium text-white">{{ r.buyerName || '—' }}</p>
                  @if (r.buyerEmail) {
                    <p class="text-xs text-slate-500">{{ r.buyerEmail }}</p>
                  }
                </div>
                <div>
                  <span class="text-slate-500">Date</span>
                  <p class="font-medium text-white">{{ r.orderDate }}</p>
                </div>
                <div>
                  <span class="text-slate-500">Total TTC</span>
                  <p class="font-medium text-amber-200">{{ money(r.totalTtc) }}</p>
                </div>
                <div>
                  <span class="text-slate-500">Paiement</span>
                  <p class="font-medium" [class.text-emerald-400]="r.paid" [class.text-slate-400]="!r.paid">
                    {{ r.paid ? 'Payé' : 'Non payé' }}
                  </p>
                </div>
              </div>
            </div>

            <ul class="mt-5 space-y-2">
              @for (tx of r.transactions; track tx.transactionId) {
                <li class="rounded-lg border border-slate-800 bg-slate-950/40 px-4 py-3 text-sm">
                  <p class="font-medium text-white">{{ tx.title }}</p>
                  <p class="mt-1 text-xs text-slate-500">
                    Listing {{ tx.listingId }} · Qté {{ tx.quantity }} · {{ money(tx.lineTotalTtc) }} TTC
                  </p>
                </li>
              }
            </ul>
          }
        </div>

        <footer class="shrink-0 border-t border-slate-800 bg-slate-950/40 px-6 py-4 sm:px-8">
          @if (step() === 'products') {
            <div class="flex flex-wrap items-center justify-end gap-2 sm:gap-3">
              <button
                type="button"
                class="rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-medium text-slate-300 transition hover:border-slate-600 hover:bg-slate-800 hover:text-white disabled:opacity-50"
                [disabled]="busy() || committing()"
                (click)="skipProduct.emit()"
              >
                Ignorer
              </button>
              <button
                type="button"
                class="rounded-lg border border-slate-600 px-4 py-2.5 text-sm font-medium text-slate-200 transition hover:bg-slate-800 disabled:opacity-50"
                [disabled]="busy() || committing()"
                (click)="acceptAllProducts.emit()"
              >
                Tout valider (reste)
              </button>
              <button
                type="button"
                class="rounded-lg bg-amber-600 px-5 py-2.5 text-sm font-semibold text-white shadow-lg shadow-amber-900/30 transition hover:bg-amber-500 disabled:opacity-50"
                [disabled]="productFormInvalid() || busy() || committing()"
                (click)="acceptProduct.emit()"
              >
                Valider ce produit
              </button>
            </div>
          }
          @if (step() === 'receipts') {
            <div class="flex flex-wrap items-center justify-end gap-2 sm:gap-3">
              <button
                type="button"
                class="rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-medium text-slate-300 transition hover:border-slate-600 hover:bg-slate-800 hover:text-white disabled:opacity-50"
                [disabled]="busy() || committing()"
                (click)="skipReceipt.emit()"
              >
                Ignorer
              </button>
              <button
                type="button"
                class="rounded-lg border border-slate-600 px-4 py-2.5 text-sm font-medium text-slate-200 transition hover:bg-slate-800 disabled:opacity-50"
                [disabled]="busy() || committing()"
                (click)="acceptAllReceipts.emit()"
              >
                Tout importer (reste)
              </button>
              <button
                type="button"
                class="rounded-lg bg-amber-600 px-5 py-2.5 text-sm font-semibold text-white shadow-lg shadow-amber-900/30 transition hover:bg-amber-500 disabled:opacity-50"
                [disabled]="busy() || committing()"
                (click)="acceptReceipt.emit()"
              >
                {{ committing() ? 'Import…' : 'Importer cette commande' }}
              </button>
            </div>
          }
        </footer>
      </div>
    </div>
  `,
})
export class EtsySyncReviewModalComponent {
  readonly step = input.required<'products' | 'receipts'>();
  readonly products = input.required<EtsySyncProductDraft[]>();
  readonly receipts = input.required<EtsySyncReceiptDraft[]>();
  readonly productIndex = input.required<number>();
  readonly receiptIndex = input.required<number>();
  readonly productProgressPercent = input.required<number>();
  readonly receiptProgressPercent = input.required<number>();
  readonly currentProduct = input<EtsySyncProductDraft | null>(null);
  readonly currentReceipt = input<EtsySyncReceiptDraft | null>(null);
  readonly productForm = input.required<FormGroup>();
  readonly busy = input(false);
  readonly committing = input(false);
  readonly productFormInvalid = input(true);

  readonly closed = output<void>();
  readonly backdropClose = output<void>();
  readonly skipProduct = output<void>();
  readonly acceptAllProducts = output<void>();
  readonly acceptProduct = output<void>();
  readonly skipReceipt = output<void>();
  readonly acceptAllReceipts = output<void>();
  readonly acceptReceipt = output<void>();

  money(v: number): string {
    return formatMoney(v);
  }
}
