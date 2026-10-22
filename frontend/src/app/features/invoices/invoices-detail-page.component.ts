import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Invoice } from '../../shared/models/invoice.model';
import { formatMoney } from '../../shared/utils/money.util';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { IconActionComponent } from '../../shared/ui/icon-action.component';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { InvoicesApiService } from './invoices-api.service';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

@Component({
  selector: 'app-invoices-detail-page',
  standalone: true,
  imports: [RouterLink, DatePipe, ErrorBannerComponent, LoadingStateComponent, IconActionComponent, LucideIconComponent],
  template: `
    @if (loading()) {
      <app-loading-state />
    } @else if (invoice()) {
      <div class="space-y-6">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div>
            <a routerLink="/invoices" class="text-sm text-slate-400 hover:text-white">← Factures</a>
            <h1 class="mt-2 text-2xl font-bold text-white">Facture {{ invoice()!.number }}</h1>
            <p class="text-sm text-slate-400">
              {{ invoice()!.status }} · Échéance {{ invoice()!.dueDate | date: 'shortDate' }}
              @if (invoice()!.merchant || invoice()!.orderNumber) {
                ·
                @if (invoice()!.merchant) {
                  {{ invoice()!.merchant }}
                }
                @if (invoice()!.orderNumber) {
                  commande {{ invoice()!.orderNumber }}
                }
              }
            </p>
          </div>
          <button
            type="button"
            class="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500 disabled:opacity-50"
            [disabled]="downloading()"
            (click)="download()"
          >
            @if (downloading()) {
              <app-lucide-icon name="loader" [size]="16" [spin]="true" />
            } @else {
              <app-lucide-icon name="download" [size]="16" />
            }
            Télécharger PDF
          </button>
        </div>

        @if (error()) {
          <app-error-banner [message]="error()!" />
        }

        <div class="grid gap-4 sm:grid-cols-3">
          <div class="rounded-xl border border-slate-800 p-4">
            <p class="text-xs text-slate-500">Total TTC</p>
            <p class="text-xl font-semibold text-white">{{ money(invoice()!.totalTtc) }}</p>
          </div>
          <div class="rounded-xl border border-slate-800 p-4">
            <p class="text-xs text-slate-500">Montant payé</p>
            <p class="text-xl font-semibold text-white">{{ money(invoice()!.amountPaid) }}</p>
          </div>
          <div class="rounded-xl border border-slate-800 p-4">
            <p class="text-xs text-slate-500">Reste dû</p>
            <p class="text-xl font-semibold text-white">{{ money(invoice()!.totalTtc - invoice()!.amountPaid) }}</p>
          </div>
        </div>

        <div class="overflow-hidden rounded-xl border border-slate-800">
          <table class="min-w-full divide-y divide-slate-800 text-sm">
            <thead class="bg-slate-900/80 text-left text-xs uppercase text-slate-500">
              <tr>
                <th class="px-4 py-3">Description</th>
                <th class="px-4 py-3">Qté</th>
                <th class="px-4 py-3">PU HT</th>
                <th class="px-4 py-3">TTC</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800">
              @for (line of invoice()!.lines; track line.id) {
                <tr>
                  <td class="px-4 py-3 text-white">
                    {{ line.description }}
                    @if (line.productId) {
                      <div class="mt-2">
                        <app-icon-action
                          icon="package"
                          label="Voir produit catalogue"
                          variant="brand"
                          [link]="['/products', line.productId, 'edit']"
                        />
                      </div>
                    } @else if (line.etsyListingId) {
                      <span class="mt-1 block text-xs text-slate-500">Listing Etsy #{{ line.etsyListingId }}</span>
                    }
                  </td>
                  <td class="px-4 py-3 text-slate-400">{{ line.quantity }}</td>
                  <td class="px-4 py-3 text-slate-400">{{ money(line.unitPriceHt) }}</td>
                  <td class="px-4 py-3">{{ money(line.lineTotalTtc) }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    }
  `,
})
export class InvoicesDetailPageComponent implements OnInit {
  private readonly api = inject(InvoicesApiService);
  private readonly route = inject(ActivatedRoute);

  readonly loading = signal(true);
  readonly downloading = signal(false);
  readonly error = signal<string | null>(null);
  readonly invoice = signal<Invoice | null>(null);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.api.get(id).subscribe({
      next: (inv) => {
        this.invoice.set(inv);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Facture introuvable'));
        this.loading.set(false);
      },
    });
  }

  money(v: number): string {
    return formatMoney(v);
  }

  download(): void {
    const inv = this.invoice();
    if (!inv || this.downloading()) {
      return;
    }
    this.downloading.set(true);
    this.error.set(null);
    this.api.downloadPdf(inv.id).subscribe({
      next: (blob) => {
        const pdfBlob = blob.type === 'application/pdf' ? blob : new Blob([blob], { type: 'application/pdf' });
        const url = URL.createObjectURL(pdfBlob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `facture-${inv.number}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
        this.downloading.set(false);
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Téléchargement impossible'));
        this.downloading.set(false);
      },
    });
  }
}
