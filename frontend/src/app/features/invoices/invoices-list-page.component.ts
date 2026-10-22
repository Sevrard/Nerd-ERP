import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { workspaceAllowsInvoicePdfImport, workspaceShopSyncLabel } from '../../core/workspace/workspace-shop-sync.config';
import { Invoice, invoiceCustomerDisplayName } from '../../shared/models/invoice.model';
import { formatMoney } from '../../shared/utils/money.util';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { IconActionComponent } from '../../shared/ui/icon-action.component';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { InvoicesApiService } from './invoices-api.service';
import { InvoiceScanPanelComponent } from './invoice-scan-panel.component';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

@Component({
  selector: 'app-invoices-list-page',
  standalone: true,
  imports: [
    DatePipe,
    LoadingStateComponent,
    EmptyStateComponent,
    ErrorBannerComponent,
    IconActionComponent,
    LucideIconComponent,
    InvoiceScanPanelComponent,
  ],
  template: `
    <div class="space-y-6">
      <h1 class="text-2xl font-bold text-white">Factures</h1>

      @if (pdfImportEnabled()) {
        <app-invoice-scan-panel (invoicesChanged)="reload()" />
      } @else if (shopSyncLabel()) {
        <p class="rounded-xl border border-slate-800 bg-slate-900/60 px-4 py-3 text-sm text-slate-400">
          Import PDF désactivé : les factures de ce workspace proviennent de la synchronisation
          <span class="font-medium text-orange-300">{{ shopSyncLabel() }}</span>
          (dashboard).
        </p>
      }

      @if (error()) {
        <app-error-banner [message]="error()!" />
      }
      @if (loading()) {
        <app-loading-state />
      } @else if (!items().length) {
        <app-empty-state title="Aucune facture." />
      } @else {
        <div class="overflow-hidden rounded-xl border border-slate-800">
          <table class="min-w-full divide-y divide-slate-800 text-sm">
            <thead class="bg-slate-900/80 text-left text-xs uppercase text-slate-500">
              <tr>
                <th class="w-10 px-2 py-3"></th>
                <th class="px-4 py-3">Numéro</th>
                <th class="px-4 py-3">Client</th>
                <th class="px-4 py-3">Marchand</th>
                <th class="px-4 py-3">N° commande</th>
                <th class="px-4 py-3">Statut</th>
                <th class="px-4 py-3">Total TTC</th>
                <th class="px-4 py-3">Payé</th>
                <th class="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800">
              @for (inv of items(); track inv.id) {
                <tr class="hover:bg-slate-900/50" [class.bg-slate-900/30]="isExpanded(inv.id)">
                  <td class="px-2 py-3 align-middle">
                    <button
                      type="button"
                      class="inline-flex size-8 items-center justify-center rounded-lg border border-slate-700/80 text-slate-400 transition hover:bg-slate-800 hover:text-white"
                      [attr.aria-expanded]="isExpanded(inv.id)"
                      [attr.aria-label]="isExpanded(inv.id) ? 'Replier le détail' : 'Afficher le détail'"
                      (click)="toggleExpanded(inv.id)"
                    >
                      <span class="inline-flex transition-transform" [class.rotate-90]="isExpanded(inv.id)">
                        <app-lucide-icon name="chevron" [size]="16" />
                      </span>
                    </button>
                  </td>
                  <td class="px-4 py-3 font-medium text-white">{{ inv.number }}</td>
                  <td class="px-4 py-3 text-slate-300">{{ customerName(inv) }}</td>
                  <td class="px-4 py-3">
                    @if (merchantLogo(inv.merchant); as logo) {
                      <img
                        [src]="logo.src"
                        [alt]="logo.alt"
                        [title]="logo.alt"
                        class="h-7 w-auto max-w-[5.5rem] rounded-sm object-contain"
                        width="28"
                        height="28"
                      />
                    } @else if (inv.merchant) {
                      <span class="text-slate-400">{{ inv.merchant }}</span>
                    } @else {
                      <span class="text-slate-500">—</span>
                    }
                  </td>
                  <td class="px-4 py-3 text-slate-400">{{ inv.orderNumber || '—' }}</td>
                  <td class="px-4 py-3 text-slate-400">{{ inv.status }}</td>
                  <td class="px-4 py-3">{{ money(inv.totalTtc) }}</td>
                  <td class="px-4 py-3">{{ money(inv.amountPaid) }}</td>
                  <td class="px-4 py-3">
                    <div class="flex items-center justify-end gap-1">
                      <app-icon-action
                        icon="eye"
                        label="Voir le détail"
                        variant="brand"
                        [link]="['/invoices', inv.id]"
                      />
                      <app-icon-action
                        icon="download"
                        label="Télécharger le PDF"
                        variant="default"
                        [loading]="downloadingId() === inv.id"
                        (action)="downloadPdf(inv)"
                      />
                      <app-icon-action
                        icon="trash"
                        label="Supprimer la facture"
                        variant="danger"
                        [loading]="deletingId() === inv.id"
                        (action)="confirmDelete(inv)"
                      />
                    </div>
                  </td>
                </tr>
                @if (isExpanded(inv.id)) {
                  <tr class="bg-slate-950/70">
                    <td colspan="9" class="px-4 py-5 sm:px-6">
                      <div class="space-y-4 border-l-2 border-brand-500/40 pl-4 sm:pl-5">
                        <div class="flex flex-wrap gap-x-8 gap-y-2 text-sm">
                          <div>
                            <span class="text-slate-500">Émission</span>
                            <p class="text-white">{{ inv.issueDate | date: 'mediumDate' }}</p>
                          </div>
                          <div>
                            <span class="text-slate-500">Échéance</span>
                            <p class="text-white">{{ inv.dueDate | date: 'mediumDate' }}</p>
                          </div>
                          @if (inv.externalReference) {
                            <div>
                              <span class="text-slate-500">Réf. externe</span>
                              <p class="text-white">{{ inv.externalReference }}</p>
                            </div>
                          }
                          <div>
                            <span class="text-slate-500">Reste dû</span>
                            <p class="text-white">{{ money(inv.totalTtc - inv.amountPaid) }}</p>
                          </div>
                        </div>

                        @if (inv.lines.length) {
                          <div class="overflow-hidden rounded-lg border border-slate-800">
                            <table class="min-w-full text-left text-sm">
                              <thead class="bg-slate-900/90 text-xs uppercase text-slate-500">
                                <tr>
                                  <th class="px-3 py-2">Ligne</th>
                                  <th class="px-3 py-2">Produit / Etsy</th>
                                  <th class="px-3 py-2">Qté</th>
                                  <th class="px-3 py-2">TTC</th>
                                </tr>
                              </thead>
                              <tbody class="divide-y divide-slate-800/80">
                                @for (line of inv.lines; track line.id) {
                                  <tr>
                                    <td class="max-w-md px-3 py-2 text-slate-200">{{ line.description }}</td>
                                    <td class="px-3 py-2 text-xs">
                                      @if (line.productId) {
                                        <app-icon-action
                                          icon="package"
                                          label="Ouvrir le produit catalogue"
                                          variant="brand"
                                          [link]="['/products', line.productId, 'edit']"
                                        />
                                      } @else if (line.etsyListingId) {
                                        <span class="text-amber-200/90">Listing Etsy #{{ line.etsyListingId }}</span>
                                        <span class="text-slate-500"> · non lié au catalogue</span>
                                      } @else {
                                        <span class="text-slate-500">—</span>
                                      }
                                      @if (line.etsyTransactionId) {
                                        <span class="mt-0.5 block text-slate-600">Transaction {{ line.etsyTransactionId }}</span>
                                      }
                                    </td>
                                    <td class="px-3 py-2 text-slate-400">{{ line.quantity }}</td>
                                    <td class="px-3 py-2 text-white">{{ money(line.lineTotalTtc) }}</td>
                                  </tr>
                                }
                              </tbody>
                            </table>
                          </div>
                        } @else {
                          <p class="text-sm text-slate-500">Aucune ligne sur cette facture.</p>
                        }

                        <div class="flex flex-wrap items-center gap-1 pt-1">
                          <app-icon-action
                            icon="external-link"
                            label="Page complète"
                            variant="default"
                            [link]="['/invoices', inv.id]"
                          />
                          <app-icon-action
                            icon="download"
                            label="Télécharger PDF"
                            variant="default"
                            [loading]="downloadingId() === inv.id"
                            (action)="downloadPdf(inv)"
                          />
                        </div>
                      </div>
                    </td>
                  </tr>
                }
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
})
export class InvoicesListPageComponent {
  private readonly api = inject(InvoicesApiService);
  private readonly workspaces = inject(WorkspaceService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly items = signal<Invoice[]>([]);
  readonly deletingId = signal<string | null>(null);
  readonly downloadingId = signal<string | null>(null);
  readonly expandedId = signal<string | null>(null);

  readonly shopSyncLabel = computed(() => workspaceShopSyncLabel(this.workspaces.selectedWorkspace()?.slug));
  readonly pdfImportEnabled = computed(() => workspaceAllowsInvoicePdfImport(this.workspaces.selectedWorkspace()?.slug));

  readonly customerName = invoiceCustomerDisplayName;

  constructor() {
    effect(() => {
      this.workspaces.generation();
      this.reload();
    });
  }

  money(v: number): string {
    return formatMoney(v);
  }

  isExpanded(id: string): boolean {
    return this.expandedId() === id;
  }

  toggleExpanded(id: string): void {
    this.expandedId.update((current) => (current === id ? null : id));
  }

  merchantLogo(merchant: string | null | undefined): { src: string; alt: string } | null {
    const key = merchant?.trim().toLowerCase().replace(/\s+/g, '-');
    if (!key) {
      return null;
    }
    if (key === 'etsy') {
      return { src: '/branding/etsy-icon.png', alt: 'Etsy' };
    }
    if (key === 'nerd-forge') {
      return { src: '/branding/nerd-forge-logo.png', alt: 'Nerd-Forge' };
    }
    return null;
  }

  downloadPdf(inv: Invoice): void {
    if (this.downloadingId() === inv.id) {
      return;
    }
    this.downloadingId.set(inv.id);
    this.api.downloadPdf(inv.id).subscribe({
      next: (blob) => {
        const pdfBlob = blob.type === 'application/pdf' ? blob : new Blob([blob], { type: 'application/pdf' });
        const url = URL.createObjectURL(pdfBlob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `facture-${inv.number}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
        this.downloadingId.set(null);
      },
      error: () => this.downloadingId.set(null),
    });
  }

  confirmDelete(inv: Invoice): void {
    const label = this.customerName(inv);
    const paidNote =
      inv.amountPaid > 0 || inv.status === 'PAID' || inv.status === 'PARTIALLY_PAID'
        ? '\n\nLes liens avec les encaissements seront retirés (la facture disparaît du suivi).'
        : '';
    const ok = confirm(
      `Supprimer la facture ${inv.number} (${label}) ?${paidNote}\n\nCette action est définitive.`,
    );
    if (!ok) {
      return;
    }
    this.error.set(null);
    this.deletingId.set(inv.id);
    this.api.delete(inv.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        if (this.expandedId() === inv.id) {
          this.expandedId.set(null);
        }
        this.reload();
      },
      error: (err) => {
        this.deletingId.set(null);
        this.error.set(apiErrorMessage(err, 'Suppression impossible'));
      },
    });
  }

  reload(): void {
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
        this.items.set(page.content);
        this.loading.set(false);
      },
      error: (err) => {
        if (this.workspaces.isStale(gen)) {
          return;
        }
        this.error.set(apiErrorMessage(err, 'Erreur'));
        this.loading.set(false);
      },
    });
  }
}
