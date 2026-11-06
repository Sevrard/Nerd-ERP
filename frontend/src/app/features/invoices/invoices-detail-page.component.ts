import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs/operators';
import { of } from 'rxjs';
import { DocumentEmailPayload, INVOICE_STATUS_LABELS, Invoice } from '../../shared/models/invoice.model';
import { formatMoney } from '../../shared/utils/money.util';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { IconActionComponent } from '../../shared/ui/icon-action.component';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { DocumentEmailDialogComponent } from '../../shared/ui/document-email-dialog.component';
import { InvoicesApiService } from './invoices-api.service';
import { CustomersApiService } from '../customers/customers-api.service';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

type EmailMode = 'send' | 'remind';

@Component({
  selector: 'app-invoices-detail-page',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    ReactiveFormsModule,
    ErrorBannerComponent,
    LoadingStateComponent,
    IconActionComponent,
    LucideIconComponent,
    DocumentEmailDialogComponent,
  ],
  template: `
    @if (loading()) {
      <app-loading-state />
    } @else if (invoice()) {
      <div class="space-y-6">
        @if (toast()) {
          <div
            class="fixed bottom-6 right-6 z-50 flex items-center gap-2 rounded-xl bg-cyan-500 px-5 py-3 text-sm font-semibold text-slate-950 shadow-2xl"
          >
            <app-lucide-icon name="sparkles" [size]="18" />
            {{ toast() }}
          </div>
        }

        <div class="flex flex-wrap items-start justify-between gap-4">
          <div>
            <a routerLink="/invoices" class="text-sm text-slate-400 hover:text-white">← Factures</a>
            <div class="mt-2 flex flex-wrap items-center gap-2">
              <h1 class="text-2xl font-bold text-white">
                {{ isCreditNote() ? 'Avoir' : isDeposit() ? "Facture d'acompte" : 'Facture' }} {{ invoice()!.number }}
              </h1>
              <span
                class="rounded-full border px-2.5 py-0.5 text-xs font-semibold"
                [class]="statusClasses()"
              >
                {{ statusLabel() }}
              </span>
              @if (isCreditNote()) {
                <span class="rounded-full border border-violet-500/30 bg-violet-500/10 px-2.5 py-0.5 text-xs font-semibold text-violet-300">
                  Avoir
                </span>
              } @else if (isDeposit()) {
                <span class="rounded-full border border-amber-500/30 bg-amber-500/10 px-2.5 py-0.5 text-xs font-semibold text-amber-300">
                  Acompte
                </span>
              }
            </div>
            <p class="mt-1 text-sm text-slate-400">
              Émise le {{ invoice()!.issueDate | date: 'shortDate' }}
              @if (!isCreditNote()) {
                · Échéance {{ invoice()!.dueDate | date: 'shortDate' }}
              }
              @if (invoice()!.merchant || invoice()!.orderNumber) {
                ·
                @if (invoice()!.merchant) {
                  {{ invoice()!.merchant }}
                }
                @if (invoice()!.orderNumber) {
                  commande {{ invoice()!.orderNumber }}
                }
              }
              @if (invoice()!.sentAt) {
                · Envoyée par e-mail le {{ invoice()!.sentAt | date: 'short' }}
              }
              @if (invoice()!.reminderCount > 0) {
                · {{ invoice()!.reminderCount }} relance{{ invoice()!.reminderCount > 1 ? 's' : '' }}
                (dernière le {{ invoice()!.lastReminderAt | date: 'shortDate' }})
              }
            </p>
            @if (isCreditNote() && invoice()!.creditedInvoiceId) {
              <p class="mt-1 text-sm text-slate-400">
                Avoir sur la facture
                <a
                  class="text-brand-400 hover:underline"
                  [routerLink]="['/invoices', invoice()!.creditedInvoiceId]"
                  >{{ invoice()!.creditedInvoiceNumber ?? 'd’origine' }}</a
                >
                @if (invoice()!.creditReason) {
                  — {{ invoice()!.creditReason }}
                }
              </p>
            }
            @if (invoice()!.depositInvoice; as dep) {
              <p class="mt-1 text-sm text-slate-400">
                Facture de solde : acompte
                <a class="text-brand-400 hover:underline" [routerLink]="['/invoices', dep.id]">{{ dep.number }}</a>
                ({{ money(dep.totalTtc) }}) déduit.
              </p>
            }
            @if (invoice()!.balanceInvoice; as bal) {
              <p class="mt-1 text-sm text-slate-400">
                Déduite sur la facture de solde
                <a class="text-brand-400 hover:underline" [routerLink]="['/invoices', bal.id]">{{ bal.number }}</a>
                ({{ money(bal.totalTtc) }}).
              </p>
            } @else if (isDeposit() && invoice()!.sourceQuotationId) {
              <p class="mt-1 text-sm text-slate-400">
                Le solde sera facturé depuis le
                <a class="text-brand-400 hover:underline" [routerLink]="['/quotations', invoice()!.sourceQuotationId]">devis d'origine</a>.
              </p>
            }
          </div>
          <div class="flex flex-wrap items-center gap-2">
            @if (canEmail()) {
              <button
                type="button"
                class="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-500 disabled:opacity-50"
                [disabled]="busy()"
                (click)="openEmail('send')"
              >
                <app-lucide-icon name="mail" [size]="16" />
                {{ invoice()!.sentAt ? 'Renvoyer par e-mail' : 'Envoyer par e-mail' }}
              </button>
            }
            @if (canRemind()) {
              <button
                type="button"
                class="inline-flex items-center gap-2 rounded-lg border border-amber-500/40 px-4 py-2 text-sm font-medium text-amber-300 hover:bg-amber-950/40 disabled:opacity-50"
                [disabled]="busy()"
                (click)="openEmail('remind')"
              >
                <app-lucide-icon name="clock" [size]="16" />
                Relancer
              </button>
            }
            @if (canCredit()) {
              <button
                type="button"
                class="inline-flex items-center gap-2 rounded-lg border border-violet-500/40 px-4 py-2 text-sm font-medium text-violet-300 hover:bg-violet-950/40 disabled:opacity-50"
                [disabled]="busy()"
                (click)="creditDialogOpen.set(true)"
              >
                <app-lucide-icon name="refresh-cw" [size]="16" />
                Créer un avoir
              </button>
            }
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
        </div>

        @if (error()) {
          <app-error-banner [message]="error()!" />
        }

        @if (invoice()!.status === 'CANCELLED') {
          <div class="rounded-xl border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
            Cette facture a été annulée par avoir. Elle reste consultable à titre de justificatif ; aucun paiement ne peut y être imputé.
          </div>
        } @else if (!isCreditNote() && invoice()!.issuedAt) {
          <p class="text-xs text-slate-500">
            Facture émise et numérotée : elle ne peut plus être modifiée ni supprimée. Toute correction passe par un avoir.
          </p>
        }

        <div class="grid gap-4 sm:grid-cols-3">
          <div class="rounded-xl border border-slate-800 p-4">
            <p class="text-xs text-slate-500">Total TTC</p>
            <p class="text-xl font-semibold text-white">{{ money(invoice()!.totalTtc) }}</p>
          </div>
          @if (!isCreditNote()) {
            <div class="rounded-xl border border-slate-800 p-4">
              <p class="text-xs text-slate-500">Montant payé</p>
              <p class="text-xl font-semibold text-white">{{ money(invoice()!.amountPaid) }}</p>
            </div>
            <div class="rounded-xl border border-slate-800 p-4">
              <p class="text-xs text-slate-500">Reste dû</p>
              <p class="text-xl font-semibold text-white">{{ money(remaining()) }}</p>
            </div>
          } @else {
            <div class="rounded-xl border border-slate-800 p-4 sm:col-span-2">
              <p class="text-xs text-slate-500">Nature</p>
              <p class="text-sm text-slate-300">
                {{ invoice()!.status === 'PAID' ? 'Avoir soldé : la facture d’origine est annulée.' : 'Avoir ouvert : à déduire du prochain règlement ou à rembourser.' }}
              </p>
            </div>
          }
        </div>

        @if (invoice()!.creditNotes.length) {
          <div class="rounded-xl border border-violet-500/20 bg-violet-500/5 p-4">
            <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-violet-300">Avoirs émis sur cette facture</p>
            <ul class="space-y-1 text-sm">
              @for (cn of invoice()!.creditNotes; track cn.id) {
                <li class="flex items-center justify-between gap-3">
                  <a class="text-brand-400 hover:underline" [routerLink]="['/invoices', cn.id]">{{ cn.number }}</a>
                  <span class="font-mono text-slate-300">{{ money(cn.totalTtc) }}</span>
                </li>
              }
            </ul>
          </div>
        }

        <div class="overflow-x-auto rounded-xl border border-slate-800">
          <table class="min-w-full divide-y divide-slate-800 text-sm">
            <thead class="bg-slate-900/80 text-left text-xs uppercase text-slate-500">
              <tr>
                <th class="px-4 py-3">Description</th>
                <th class="px-4 py-3">Qté</th>
                <th class="px-4 py-3">PU HT</th>
                <th class="px-4 py-3">TVA</th>
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
                    }
                  </td>
                  <td class="px-4 py-3 text-slate-400">{{ line.quantity }}</td>
                  <td class="px-4 py-3 text-slate-400">
                    {{ money(line.unitPriceHt) }}
                    @if (line.discountPercent > 0) {
                      <span class="block text-xs text-rose-400">remise -{{ line.discountPercent }} %</span>
                    }
                  </td>
                  <td class="px-4 py-3 text-slate-400">{{ line.vatRate }} %</td>
                  <td class="px-4 py-3">{{ money(line.lineTotalTtc) }}</td>
                </tr>
              }
            </tbody>
            <tfoot class="bg-slate-900/60 text-sm">
              @if (invoice()!.discountAmountHt > 0) {
                <tr>
                  <td colspan="4" class="px-4 py-2 text-right text-slate-500">Sous-total HT</td>
                  <td class="px-4 py-2 font-mono text-slate-300">{{ money(invoice()!.subtotalHt) }}</td>
                </tr>
                <tr>
                  <td colspan="4" class="px-4 py-2 text-right text-rose-400">Remise ({{ invoice()!.discountPercent }} %)</td>
                  <td class="px-4 py-2 font-mono text-rose-400">-{{ money(invoice()!.discountAmountHt) }}</td>
                </tr>
              }
              <tr>
                <td colspan="4" class="px-4 py-2 text-right text-slate-500">Total HT</td>
                <td class="px-4 py-2 font-mono text-slate-300">{{ money(invoice()!.totalHt) }}</td>
              </tr>
              @for (v of invoice()!.vatBreakdown; track v.rate) {
                <tr>
                  <td colspan="4" class="px-4 py-2 text-right text-slate-500">TVA {{ v.rate }} % (base {{ money(v.baseHt) }})</td>
                  <td class="px-4 py-2 font-mono text-slate-300">{{ money(v.vatAmount) }}</td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="4" class="px-4 py-2 text-right text-slate-500">TVA</td>
                  <td class="px-4 py-2 font-mono text-slate-300">{{ money(invoice()!.totalVat) }}</td>
                </tr>
              }
              <tr class="border-t border-slate-800">
                <td colspan="4" class="px-4 py-3 text-right font-semibold text-white">Total TTC</td>
                <td class="px-4 py-3 font-mono font-semibold text-white">{{ money(invoice()!.totalTtc) }}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>

      @if (emailMode()) {
        <app-document-email-dialog
          [title]="emailMode() === 'remind' ? 'Relancer le client' : (isCreditNote() ? 'Envoyer l’avoir' : 'Envoyer la facture')"
          [subtitle]="
            emailMode() === 'remind'
              ? 'Rappel de paiement avec le reste dû, la date d’échéance et vos coordonnées bancaires. Le PDF est joint.'
              : 'Le PDF est joint ; vos coordonnées bancaires figurent dans le message.'
          "
          [defaultTo]="customerEmail()"
          [submitLabel]="emailMode() === 'remind' ? 'Envoyer la relance' : 'Envoyer'"
          [busy]="busy()"
          [error]="dialogError()"
          (send)="sendEmail($event)"
          (close)="emailMode.set(null)"
        />
      }

      @if (creditDialogOpen()) {
        <div
          class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 p-4"
          role="dialog"
          aria-modal="true"
          aria-label="Créer un avoir"
        >
          <form
            class="w-full max-w-lg space-y-4 rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-2xl"
            [formGroup]="creditForm"
            (ngSubmit)="createCreditNote()"
          >
            <div>
              <h2 class="text-lg font-semibold text-white">Créer un avoir</h2>
              <p class="mt-1 text-sm text-slate-400">
                L'avoir reprend l'intégralité de la facture ({{ money(invoice()!.totalTtc) }} TTC) et porte son propre numéro.
                @if (invoice()!.amountPaid === 0) {
                  La facture d'origine sera annulée.
                } @else {
                  La facture ayant déjà été encaissée en partie, elle reste en l'état et l'avoir est à rembourser ou à déduire.
                }
              </p>
            </div>
            @if (dialogError()) {
              <p class="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{{ dialogError() }}</p>
            }
            <div>
              <label class="mb-1.5 block text-sm text-slate-400" for="credit-reason">Motif (obligatoire, reporté sur l'avoir)</label>
              <textarea
                id="credit-reason"
                formControlName="reason"
                rows="3"
                placeholder="Erreur de facturation, prestation annulée, geste commercial…"
                class="w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-white focus:border-cyan-500/50 focus:outline-none"
              ></textarea>
            </div>
            <div class="flex justify-end gap-2 pt-2">
              <button
                type="button"
                class="rounded-lg border border-slate-700 px-4 py-2 text-sm text-slate-200 hover:bg-slate-800"
                (click)="creditDialogOpen.set(false)"
              >
                Annuler
              </button>
              <button
                type="submit"
                class="inline-flex items-center gap-2 rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-500 disabled:opacity-50"
                [disabled]="creditForm.invalid || busy()"
              >
                @if (busy()) {
                  <app-lucide-icon name="loader" [size]="16" [spin]="true" />
                }
                Émettre l'avoir
              </button>
            </div>
          </form>
        </div>
      }
    }
  `,
})
export class InvoicesDetailPageComponent implements OnInit {
  private readonly api = inject(InvoicesApiService);
  private readonly customersApi = inject(CustomersApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(true);
  readonly downloading = signal(false);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly dialogError = signal<string | null>(null);
  readonly toast = signal<string | null>(null);
  readonly invoice = signal<Invoice | null>(null);
  readonly customerEmail = signal<string | null>(null);
  readonly emailMode = signal<EmailMode | null>(null);
  readonly creditDialogOpen = signal(false);

  readonly creditForm = this.fb.nonNullable.group({
    reason: ['', [Validators.required, Validators.maxLength(512)]],
  });

  readonly isCreditNote = computed(() => this.invoice()?.invoiceType === 'CREDIT_NOTE');
  readonly isDeposit = computed(() => this.invoice()?.invoiceType === 'DEPOSIT');
  readonly remaining = computed(() => {
    const inv = this.invoice();
    return inv ? Math.max(0, inv.totalTtc - inv.amountPaid) : 0;
  });
  readonly canEmail = computed(() => {
    const inv = this.invoice();
    return !!inv && inv.status !== 'DRAFT' && !!inv.issuedAt;
  });
  readonly canRemind = computed(() => {
    const inv = this.invoice();
    return !!inv && !this.isCreditNote() && (inv.status === 'ISSUED' || inv.status === 'PARTIALLY_PAID');
  });
  readonly canCredit = computed(() => {
    const inv = this.invoice();
    return !!inv && !this.isCreditNote() && !!inv.issuedAt && inv.status !== 'DRAFT' && inv.status !== 'CANCELLED';
  });

  ngOnInit(): void {
    this.route.paramMap
      .pipe(
        switchMap((params) => {
          const id = params.get('id');
          this.loading.set(true);
          this.error.set(null);
          return id ? this.api.get(id) : of(null);
        }),
      )
      .subscribe({
        next: (inv) => {
          this.invoice.set(inv);
          this.loading.set(false);
          if (inv) {
            this.loadCustomerEmail(inv.customerId);
          }
        },
        error: (err) => {
          this.error.set(apiErrorMessage(err, 'Facture introuvable'));
          this.loading.set(false);
        },
      });
  }

  statusLabel(): string {
    const inv = this.invoice();
    return inv ? INVOICE_STATUS_LABELS[inv.status] : '';
  }

  statusClasses(): string {
    switch (this.invoice()?.status) {
      case 'PAID':
        return 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300';
      case 'PARTIALLY_PAID':
        return 'border-amber-500/30 bg-amber-500/10 text-amber-300';
      case 'CANCELLED':
        return 'border-rose-500/30 bg-rose-500/10 text-rose-300';
      case 'DRAFT':
        return 'border-slate-600 bg-slate-800 text-slate-300';
      default:
        return 'border-cyan-500/30 bg-cyan-500/10 text-cyan-300';
    }
  }

  money(v: number): string {
    return formatMoney(v);
  }

  openEmail(mode: EmailMode): void {
    this.dialogError.set(null);
    this.emailMode.set(mode);
  }

  sendEmail(payload: DocumentEmailPayload): void {
    const inv = this.invoice();
    const mode = this.emailMode();
    if (!inv || !mode || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.dialogError.set(null);
    const req = mode === 'remind' ? this.api.sendReminder(inv.id, payload) : this.api.sendByEmail(inv.id, payload);
    req.subscribe({
      next: (result) => {
        this.busy.set(false);
        this.emailMode.set(null);
        this.notify(mode === 'remind' ? `Relance envoyée à ${result.to}` : `Document envoyé à ${result.to}`);
        this.reload(inv.id);
      },
      error: (err) => {
        this.dialogError.set(apiErrorMessage(err, 'Envoi impossible'));
        this.busy.set(false);
      },
    });
  }

  createCreditNote(): void {
    const inv = this.invoice();
    if (!inv || this.creditForm.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.dialogError.set(null);
    this.api.createCreditNote(inv.id, { reason: this.creditForm.getRawValue().reason.trim() }).subscribe({
      next: (credit) => {
        this.busy.set(false);
        this.creditDialogOpen.set(false);
        this.creditForm.reset();
        this.notify(`Avoir ${credit.number} émis`);
        void this.router.navigate(['/invoices', credit.id]);
      },
      error: (err) => {
        this.dialogError.set(apiErrorMessage(err, 'Création de l’avoir impossible'));
        this.busy.set(false);
      },
    });
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
        a.download = `${this.isCreditNote() ? 'avoir' : this.isDeposit() ? 'facture-acompte' : 'facture'}-${inv.number}.pdf`;
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

  private reload(id: string): void {
    this.api.get(id).subscribe({
      next: (inv) => this.invoice.set(inv),
    });
  }

  private loadCustomerEmail(customerId: string): void {
    this.customersApi.get(customerId).subscribe({
      next: (c) => this.customerEmail.set(c.email ?? null),
      error: () => this.customerEmail.set(null),
    });
  }

  private notify(message: string): void {
    this.toast.set(message);
    setTimeout(() => this.toast.set(null), 3000);
  }
}
