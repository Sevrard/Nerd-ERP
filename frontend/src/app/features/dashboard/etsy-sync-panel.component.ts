import { Component, computed, inject, output, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { FormBuilder, Validators } from '@angular/forms';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { EtsySyncApiService } from './etsy-sync-api.service';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import {
  EtsySyncProductCommit,
  EtsySyncProductDraft,
  EtsySyncReceiptCommit,
  EtsySyncReceiptDraft,
} from '../../shared/models/etsy-sync.model';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { EtsySyncReviewModalComponent } from './etsy-sync-review-modal.component';
import { apiErrorMessage, etsyApiErrorMessage } from '../../shared/utils/api-error.util';

const ETSY_LAST_SYNC_KEY = 'nerd-erp-etsy-last-sync';

@Component({
  selector: 'app-etsy-sync-panel',
  standalone: true,
  imports: [ErrorBannerComponent, LucideIconComponent, EtsySyncReviewModalComponent],
  template: `
    <section class="rounded-2xl border border-slate-800/90 bg-slate-900/60 px-5 py-4 sm:px-6">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div class="flex items-start gap-3">
          <span
            class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-orange-500 to-amber-600 text-lg font-bold text-white shadow-lg shadow-orange-950/40"
            aria-hidden="true"
            >E</span
          >
          <div>
            <h2 class="text-lg font-semibold text-white">Synchronisation Etsy</h2>
            <p class="mt-0.5 text-sm text-slate-400">
              Listings actifs + commandes (API v3, pagination serveur).
              @if (lastSyncLabel()) {
                <span class="text-slate-500">· Dernière synchro : {{ lastSyncLabel() }}</span>
              }
            </p>
          </div>
        </div>
        <button
          type="button"
          class="inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-orange-500 to-amber-500 px-5 py-2.5 text-sm font-semibold text-white shadow-lg shadow-orange-950/30 transition hover:from-orange-400 hover:to-amber-400 disabled:opacity-50"
          [disabled]="busy() || committing()"
          (click)="startFetch()"
        >
          <app-lucide-icon name="refresh-cw" [size]="16" [spin]="busy()" />
          {{ busy() ? 'Synchronisation…' : 'Synchroniser Etsy' }}
        </button>
      </div>
      @if (error()) {
        <div class="mt-4"><app-error-banner [message]="error()!" /></div>
      }
    </section>

    @if (modal()) {
      <app-etsy-sync-review-modal
        [step]="step()"
        [products]="products()"
        [receipts]="receipts()"
        [productIndex]="productIndex()"
        [receiptIndex]="receiptIndex()"
        [productProgressPercent]="productProgressPercent()"
        [receiptProgressPercent]="receiptProgressPercent()"
        [currentProduct]="currentProduct()"
        [currentReceipt]="currentReceipt()"
        [productForm]="productForm"
        [busy]="busy()"
        [committing]="committing()"
        [productFormInvalid]="productFormInvalid()"
        (closed)="close()"
        (backdropClose)="close()"
        (skipProduct)="skipProduct()"
        (acceptAllProducts)="acceptAllProducts()"
        (acceptProduct)="acceptProduct()"
        (skipReceipt)="skipReceipt()"
        (acceptAllReceipts)="acceptAllReceipts()"
        (acceptReceipt)="acceptReceipt()"
      />
    }
  `,
})
export class EtsySyncPanelComponent {
  private readonly api = inject(EtsySyncApiService);
  private readonly workspaces = inject(WorkspaceService);
  private readonly fb = inject(FormBuilder);

  readonly syncCompleted = output<void>();

  readonly error = signal<string | null>(null);
  readonly lastSyncLabel = signal<string | null>(this.readLastSyncLabel());
  readonly modal = signal(false);
  readonly busy = signal(false);
  readonly committing = signal(false);
  readonly step = signal<'products' | 'receipts'>('products');
  readonly sessionId = signal<string | null>(null);
  readonly products = signal<EtsySyncProductDraft[]>([]);
  readonly receipts = signal<EtsySyncReceiptDraft[]>([]);
  readonly productIndex = signal(0);
  readonly receiptIndex = signal(0);
  readonly productDecisions = signal<EtsySyncProductCommit[]>([]);
  readonly receiptDecisions = signal<EtsySyncReceiptCommit[]>([]);

  readonly currentProduct = computed(() => this.products()[this.productIndex()] ?? null);
  readonly currentReceipt = computed(() => this.receipts()[this.receiptIndex()] ?? null);

  readonly productProgressPercent = computed(() => {
    const n = this.products().length;
    if (!n) return 0;
    return Math.round(((this.productIndex() + 1) / n) * 100);
  });

  readonly receiptProgressPercent = computed(() => {
    const n = this.receipts().length;
    if (!n) return 0;
    return Math.round(((this.receiptIndex() + 1) / n) * 100);
  });

  readonly productForm = this.fb.nonNullable.group({
    referenceCode: ['', Validators.required],
    name: ['', Validators.required],
    description: [''],
    unitPriceHt: [0],
    vatRate: [20],
    active: [true],
  });

  readonly productFormInvalid = computed(() => this.productForm.invalid);

  startFetch(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.fetch(this.workspaces.requireSelectedId()).subscribe({
      next: (res) => {
        this.busy.set(false);
        this.sessionId.set(res.sessionId);
        this.products.set(res.products);
        this.receipts.set(res.receipts);
        this.productDecisions.set([]);
        this.receiptDecisions.set([]);
        this.modal.set(true);
        if (res.products.length) {
          this.step.set('products');
          this.productIndex.set(0);
          this.patchProduct(res.products[0]);
        } else {
          this.step.set('receipts');
          this.receiptIndex.set(0);
        }
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(etsyApiErrorMessage(e));
      },
    });
  }

  close(): void {
    this.modal.set(false);
  }

  skipProduct(): void {
    this.decideProduct(false);
    this.nextProduct();
  }
  acceptProduct(): void {
    if (this.productForm.valid) {
      this.decideProduct(true);
      this.nextProduct();
    }
  }
  acceptAllProducts(): void {
    for (let i = this.productIndex(); i < this.products().length; i++) {
      this.productIndex.set(i);
      this.patchProduct(this.products()[i]);
      this.decideProduct(true);
    }
    this.goReceipts();
  }

  skipReceipt(): void {
    this.decideReceipt(false);
    this.nextReceipt();
  }
  acceptReceipt(): void {
    this.decideReceipt(true);
    this.nextReceipt();
  }
  acceptAllReceipts(): void {
    const list = this.receipts();
    for (let i = this.receiptIndex(); i < list.length; i++) {
      const r = list[i];
      this.recordReceiptDecision(r, !r.alreadyImported);
    }
    if (list.length) {
      this.receiptIndex.set(list.length - 1);
    }
    this.commit();
  }

  private nextProduct(): void {
    const n = this.productIndex() + 1;
    if (n >= this.products().length) {
      this.goReceipts();
      return;
    }
    this.productIndex.set(n);
    this.patchProduct(this.products()[n]);
  }

  private goReceipts(): void {
    if (!this.receipts().length) {
      this.commit();
      return;
    }
    this.step.set('receipts');
    this.receiptIndex.set(0);
  }

  private nextReceipt(): void {
    const n = this.receiptIndex() + 1;
    if (n >= this.receipts().length) {
      this.commit();
      return;
    }
    this.receiptIndex.set(n);
  }

  private patchProduct(p: EtsySyncProductDraft): void {
    this.productForm.patchValue({
      referenceCode: p.referenceCode,
      name: p.name,
      description: p.description ?? '',
      unitPriceHt: p.unitPriceHt,
      vatRate: p.vatRate,
    });
  }

  private decideProduct(importProduct: boolean): void {
    const p = this.currentProduct();
    if (!p) return;
    const raw = this.productForm.getRawValue();
    this.recordProductDecision({
      listingId: p.listingId,
      importProduct,
      referenceCode: importProduct ? raw.referenceCode : p.referenceCode,
      name: importProduct ? raw.name : p.name,
      description: importProduct ? raw.description : p.description,
      unitPriceHt: importProduct ? raw.unitPriceHt : p.unitPriceHt,
      vatRate: importProduct ? raw.vatRate : p.vatRate,
      active: importProduct ? raw.active : true,
    });
  }

  private decideReceipt(importReceipt: boolean): void {
    const r = this.currentReceipt();
    if (!r) return;
    this.recordReceiptDecision(r, importReceipt);
  }

  private recordProductDecision(entry: EtsySyncProductCommit): void {
    this.productDecisions.update((d) => [...d.filter((x) => x.listingId !== entry.listingId), entry]);
  }

  private recordReceiptDecision(receipt: EtsySyncReceiptDraft, importReceipt: boolean): void {
    const entry: EtsySyncReceiptCommit = {
      receiptId: receipt.receiptId,
      importReceipt,
      customerId: null,
      createCustomerFromBuyer: true,
    };
    this.receiptDecisions.update((d) => [...d.filter((x) => x.receiptId !== entry.receiptId), entry]);
  }

  private commit(): void {
    const sid = this.sessionId();
    if (!sid || this.committing()) {
      return;
    }
    this.committing.set(true);
    this.busy.set(true);
    this.api
      .commit(this.workspaces.requireSelectedId(), {
        sessionId: sid,
        products: this.productDecisions(),
        receipts: this.receiptDecisions(),
      })
      .pipe(
        finalize(() => {
          this.busy.set(false);
          this.committing.set(false);
        }),
      )
      .subscribe({
        next: () => {
          this.sessionId.set(null);
          this.modal.set(false);
          localStorage.setItem(ETSY_LAST_SYNC_KEY, String(Date.now()));
          this.lastSyncLabel.set(this.readLastSyncLabel());
          this.syncCompleted.emit();
        },
        error: (e) => {
          this.error.set(apiErrorMessage(e, 'Commit impossible'));
        },
      });
  }

  private readLastSyncLabel(): string | null {
    const raw = localStorage.getItem(ETSY_LAST_SYNC_KEY);
    if (!raw) {
      return null;
    }
    const ts = Number(raw);
    if (!Number.isFinite(ts)) {
      return null;
    }
    const diffMin = Math.floor((Date.now() - ts) / 60000);
    if (diffMin < 1) {
      return 'à l’instant';
    }
    if (diffMin < 60) {
      return `il y a ${diffMin} min`;
    }
    const diffH = Math.floor(diffMin / 60);
    if (diffH < 48) {
      return diffH === 1 ? 'il y a 1 heure' : `il y a ${diffH} heures`;
    }
    const diffD = Math.floor(diffH / 24);
    return diffD === 1 ? 'il y a 1 jour' : `il y a ${diffD} jours`;
  }
}
