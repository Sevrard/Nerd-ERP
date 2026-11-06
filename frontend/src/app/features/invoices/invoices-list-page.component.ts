import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { ModuleService } from '../../core/modules/module.service';
import { INVOICE_STATUS_LABELS, Invoice, invoiceCustomerDisplayName } from '../../shared/models/invoice.model';
import { formatMoney } from '../../shared/utils/money.util';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { IconActionComponent } from '../../shared/ui/icon-action.component';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { PaginatorComponent } from '../../shared/ui/paginator.component';
import { InvoicesApiService } from './invoices-api.service';
import { InvoiceScanPanelComponent } from './invoice-scan-panel.component';
import { apiErrorMessage } from '../../shared/utils/api-error.util';
import { PageState } from '../../shared/utils/page-state';

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function firstDayOfYear(): string {
  return `${new Date().getFullYear()}-01-01`;
}

@Component({
  selector: 'app-invoices-list-page',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    LoadingStateComponent,
    EmptyStateComponent,
    ErrorBannerComponent,
    IconActionComponent,
    LucideIconComponent,
    InvoiceScanPanelComponent,
    PaginatorComponent,
  ],
  template: `
    <div class="space-y-6">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <h1 class="text-xl font-bold text-white sm:text-2xl">Factures</h1>
        <form class="hidden flex-wrap items-end gap-2 sm:flex" (ngSubmit)="exportCsv()">
          <label class="block text-xs text-slate-400">
            Du
            <input
              type="date"
              class="mt-1 block rounded-lg border border-slate-700 bg-slate-900 px-2 py-1.5 text-sm text-white"
              [(ngModel)]="exportFrom"
              name="exportFrom"
              required
            />
          </label>
          <label class="block text-xs text-slate-400">
            au
            <input
              type="date"
              class="mt-1 block rounded-lg border border-slate-700 bg-slate-900 px-2 py-1.5 text-sm text-white"
              [(ngModel)]="exportTo"
              name="exportTo"
              required
            />
          </label>
          <button
            type="submit"
            class="inline-flex items-center gap-2 rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-sm font-medium text-slate-100 hover:bg-slate-700 disabled:opacity-50"
            title="Export comptable CSV (factures et avoirs émis sur la période)"
            [disabled]="exporting() || !exportFrom || !exportTo"
          >
            @if (exporting()) {
              <app-lucide-icon name="loader" [size]="16" [spin]="true" />
            } @else {
              <app-lucide-icon name="download" [size]="16" />
            }
            Export CSV
          </button>
        </form>
      </div>

      @if (modules.isEnabled('INVOICE_SCAN')) {
        <app-invoice-scan-panel (invoicesChanged)="reload()" />
      }

      @if (error()) {
        <app-error-banner [message]="error()!" />
      }
      @if (loading() && !items().length) {
        <app-loading-state />
      } @else if (!items().length) {
        <app-empty-state title="Aucune facture." />
      } @else {
        <div class="overflow-hidden rounded-xl border border-slate-800">
          <!-- Smartphone : cartes (numéro, client, statut, reste dû) + PDF. -->
          <ul class="divide-y divide-slate-800 md:hidden">
            @for (inv of items(); track inv.id) {
              <li class="flex items-center gap-2 px-3 py-3">
                <a [routerLink]="['/invoices', inv.id]" class="min-w-0 flex-1 active:opacity-70">
                  <div class="flex items-center gap-2">
                    <p class="truncate text-sm font-semibold text-white">{{ inv.number }}</p>
                    @if (inv.invoiceType === 'CREDIT_NOTE') {
                      <span class="rounded-full border border-violet-500/30 bg-violet-500/10 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-violet-300">Avoir</span>
                    } @else if (inv.invoiceType === 'DEPOSIT') {
                      <span class="rounded-full border border-amber-500/30 bg-amber-500/10 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-amber-300">Acompte</span>
                    }
                  </div>
                  <p class="mt-0.5 truncate text-xs text-slate-400">{{ customerName(inv) }}</p>
                  <p class="mt-0.5 text-[11px] text-slate-500">
                    {{ statusLabel(inv) }} · échéance {{ inv.dueDate | date: 'dd/MM/yyyy' }}
                  </p>
                </a>
                <div class="shrink-0 text-right">
                  <p class="text-sm font-semibold tabular-nums text-slate-100">{{ money(inv.totalTtc) }}</p>
                  @if (inv.totalTtc - inv.amountPaid > 0.004 && inv.invoiceType !== 'CREDIT_NOTE') {
                    <p class="text-[11px] tabular-nums text-amber-400">reste {{ money(inv.totalTtc - inv.amountPaid) }}</p>
                  } @else {
                    <p class="text-[11px] text-emerald-400">soldée</p>
                  }
                </div>
                <app-icon-action
                  icon="download"
                  label="Télécharger le PDF"
                  [loading]="downloadingId() === inv.id"
                  (action)="downloadPdf(inv)"
                />
              </li>
            }
          </ul>
          <table class="hidden min-w-full divide-y divide-slate-800 text-sm md:table">
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
                  <td class="px-4 py-3 font-medium text-white">
                    {{ inv.number }}
                    @if (inv.invoiceType === 'CREDIT_NOTE') {
                      <span class="ml-1 rounded-full border border-violet-500/30 bg-violet-500/10 px-2 py-0.5 text-[10px] font-semibold uppercase text-violet-300">
                        Avoir
                      </span>
                    } @else if (inv.invoiceType === 'DEPOSIT') {
                      <span class="ml-1 rounded-full border border-amber-500/30 bg-amber-500/10 px-2 py-0.5 text-[10px] font-semibold uppercase text-amber-300">
                        Acompte
                      </span>
                    }
                  </td>
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
                  <td class="px-4 py-3 text-slate-400">{{ statusLabel(inv) }}</td>
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
                      @if (deletable(inv)) {
                        <app-icon-action
                          icon="trash"
                          label="Supprimer la facture"
                          variant="danger"
                          [loading]="deletingId() === inv.id"
                          (action)="confirmDelete(inv)"
                        />
                      }
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
                                  <th class="px-3 py-2">Produit</th>
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
                                      } @else {
                                        <span class="text-slate-500">—</span>
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
          <app-paginator
            [meta]="page.meta()"
            [disabled]="loading()"
            (pageChange)="onPageChange($event)"
            (pageSizeChange)="onPageSizeChange($event)"
          />
        </div>
      }
    </div>
  `,
})
export class InvoicesListPageComponent {
  private readonly api = inject(InvoicesApiService);
  private readonly workspaces = inject(WorkspaceService);
  readonly modules = inject(ModuleService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly items = signal<Invoice[]>([]);
  readonly page = new PageState();
  readonly deletingId = signal<string | null>(null);
  readonly downloadingId = signal<string | null>(null);
  readonly expandedId = signal<string | null>(null);

  readonly customerName = invoiceCustomerDisplayName;
  readonly exporting = signal(false);
  exportFrom = firstDayOfYear();
  exportTo = today();

  constructor() {
    effect(() => {
      this.workspaces.generation();
      this.page.reset();
      this.reload();
    });
  }

  money(v: number): string {
    return formatMoney(v);
  }

  statusLabel(inv: Invoice): string {
    return INVOICE_STATUS_LABELS[inv.status] ?? inv.status;
  }

  /** Une facture émise par l'ERP est inaltérable (avoir) ; brouillons et imports de scan restent supprimables. */
  deletable(inv: Invoice): boolean {
    return !inv.issuedAt || (inv.invoiceType === 'STANDARD' && !!inv.merchant);
  }

  exportCsv(): void {
    const workspaceId = this.workspaces.selectedId();
    if (!workspaceId || this.exporting() || !this.exportFrom || !this.exportTo) {
      return;
    }
    this.exporting.set(true);
    this.error.set(null);
    this.api.exportCsv(workspaceId, this.exportFrom, this.exportTo).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(new Blob([blob], { type: 'text/csv;charset=utf-8' }));
        const a = document.createElement('a');
        a.href = url;
        a.download = `export-factures-${this.exportFrom}-${this.exportTo}.csv`;
        a.click();
        URL.revokeObjectURL(url);
        this.exporting.set(false);
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Export impossible'));
        this.exporting.set(false);
      },
    });
  }

  onPageChange(index: number): void {
    this.page.goTo(index);
    this.expandedId.set(null);
    this.reload();
  }

  onPageSizeChange(size: number): void {
    this.page.setSize(size);
    this.expandedId.set(null);
    this.reload();
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
        this.page.adjustAfterRemoval();
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
    this.api.list(workspaceId, this.page.query()).subscribe({
      next: (page) => {
        if (this.workspaces.isStale(gen)) {
          return;
        }
        this.items.set(page.content);
        this.page.apply(page);
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
