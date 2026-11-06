import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { PublicQuotation, QUOTATION_STATUS_LABELS } from '../../shared/models/quotation.model';
import { formatMoney } from '../../shared/utils/money.util';
import { apiErrorMessage } from '../../shared/utils/api-error.util';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { PublicQuotationApiService } from './public-quotation-api.service';

type Decision = 'accept' | 'reject';

/**
 * Page publique (lien signé reçu par e-mail) : le client consulte son devis, télécharge le PDF
 * et l'accepte ou le refuse. Aucune session ; le jeton de l'URL vaut autorisation.
 */
@Component({
  selector: 'app-public-quotation-page',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, LucideIconComponent, LoadingStateComponent],
  template: `
    <div class="min-h-screen bg-slate-950 px-4 py-10 text-slate-100">
      <div class="mx-auto w-full max-w-3xl space-y-6">
        @if (loading()) {
          <app-loading-state />
        } @else if (loadError()) {
          <div class="rounded-2xl border border-slate-800 bg-slate-900 p-8 text-center shadow-xl">
            <app-lucide-icon name="triangle-alert" [size]="32" class="mx-auto text-amber-400" />
            <h1 class="mt-4 text-xl font-bold text-white">Lien indisponible</h1>
            <p class="mt-2 text-sm text-slate-400">{{ loadError() }}</p>
          </div>
        } @else if (quotation(); as q) {
          <header class="rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-xl">
            <div class="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p class="text-xs font-semibold uppercase tracking-widest text-cyan-400">Devis {{ q.number }}</p>
                <h1 class="mt-1 text-2xl font-bold text-white">{{ q.issuerName }}</h1>
                <p class="mt-1 text-sm text-slate-400">
                  À l'attention de {{ q.customerName }}
                  @if (q.issueDate) {
                    · émis le {{ q.issueDate | date: 'longDate' }}
                  }
                  @if (q.validUntil) {
                    · valable jusqu'au {{ q.validUntil | date: 'longDate' }}
                  }
                </p>
              </div>
              <span class="rounded-full border px-3 py-1 text-xs font-semibold" [class]="statusClasses()">
                {{ statusLabel() }}
              </span>
            </div>

            @if (q.notes) {
              <p class="mt-4 whitespace-pre-line text-sm text-slate-300">{{ q.notes }}</p>
            }

            <div class="mt-5 flex flex-wrap gap-2">
              <button
                type="button"
                class="inline-flex items-center gap-2 rounded-lg border border-slate-700 bg-slate-800 px-4 py-2 text-sm font-medium text-slate-100 hover:bg-slate-700 disabled:opacity-50"
                [disabled]="downloading()"
                (click)="downloadPdf()"
              >
                @if (downloading()) {
                  <app-lucide-icon name="loader" [size]="16" [spin]="true" />
                } @else {
                  <app-lucide-icon name="download" [size]="16" />
                }
                Télécharger le PDF
              </button>
            </div>
          </header>

          <section class="overflow-x-auto rounded-2xl border border-slate-800 bg-slate-900 shadow-xl">
            <table class="min-w-full divide-y divide-slate-800 text-sm">
              <thead class="bg-slate-900/80 text-left text-xs uppercase text-slate-500">
                <tr>
                  <th class="px-4 py-3">Désignation</th>
                  <th class="px-4 py-3 text-right">Qté</th>
                  <th class="px-4 py-3 text-right">PU HT</th>
                  <th class="px-4 py-3 text-right">TVA</th>
                  <th class="px-4 py-3 text-right">Total HT</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-800">
                @for (line of q.lines; track $index) {
                  <tr>
                    <td class="px-4 py-3 text-white">{{ line.description }}</td>
                    <td class="px-4 py-3 text-right text-slate-400">{{ line.quantity }}</td>
                    <td class="px-4 py-3 text-right text-slate-400">
                      {{ money(line.unitPriceHt) }}
                      @if (line.discountPercent > 0) {
                        <span class="block text-xs text-rose-400">remise -{{ line.discountPercent }} %</span>
                      }
                    </td>
                    <td class="px-4 py-3 text-right text-slate-400">{{ line.vatRate }} %</td>
                    <td class="px-4 py-3 text-right font-mono">{{ money(line.lineTotalHt) }}</td>
                  </tr>
                }
              </tbody>
            </table>
            <dl class="space-y-1 border-t border-slate-800 bg-slate-950/40 px-4 py-4 text-sm sm:ml-auto sm:w-80">
              @if (q.discountAmountHt > 0) {
                <div class="flex justify-between text-slate-400">
                  <dt>Sous-total HT</dt>
                  <dd class="font-mono">{{ money(q.subtotalHt) }}</dd>
                </div>
                <div class="flex justify-between text-slate-400">
                  <dt>Remise {{ q.discountPercent }} %</dt>
                  <dd class="font-mono">− {{ money(q.discountAmountHt) }}</dd>
                </div>
              }
              <div class="flex justify-between text-slate-300">
                <dt>Total HT</dt>
                <dd class="font-mono">{{ money(q.totalHt) }}</dd>
              </div>
              @if (q.vatBreakdown.length > 1) {
                @for (v of q.vatBreakdown; track v.rate) {
                  <div class="flex justify-between text-slate-400">
                    <dt>TVA {{ v.rate }} % (base {{ money(v.baseHt) }})</dt>
                    <dd class="font-mono">{{ money(v.vatAmount) }}</dd>
                  </div>
                }
              } @else {
                <div class="flex justify-between text-slate-400">
                  <dt>TVA</dt>
                  <dd class="font-mono">{{ money(q.totalVat) }}</dd>
                </div>
              }
              <div class="flex justify-between border-t border-slate-800 pt-2 text-base font-semibold text-white">
                <dt>Total TTC</dt>
                <dd class="font-mono">{{ money(q.totalTtc) }}</dd>
              </div>
              @if (q.depositPercent > 0) {
                <div class="flex justify-between text-cyan-300">
                  <dt>Acompte à la commande ({{ q.depositPercent }} %)</dt>
                  <dd class="font-mono">{{ money(q.depositAmountTtc) }}</dd>
                </div>
              }
            </dl>
          </section>

          <section class="rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-xl">
            @if (q.decidable) {
              <h2 class="text-lg font-semibold text-white">Votre réponse</h2>
              <p class="mt-1 text-sm text-slate-400">
                En acceptant ce devis, vous confirmez votre commande aux conditions indiquées. Votre nom vaut signature.
              </p>
              @if (actionError()) {
                <p class="mt-4 rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-300">
                  {{ actionError() }}
                </p>
              }
              <form class="mt-4 space-y-4" [formGroup]="form">
                <div>
                  <label class="mb-1.5 block text-sm text-slate-400" for="pq-name">Votre nom et prénom</label>
                  <input
                    id="pq-name"
                    type="text"
                    formControlName="name"
                    autocomplete="name"
                    class="w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-white focus:border-cyan-500/50 focus:outline-none"
                  />
                  @if (form.controls.name.invalid && form.controls.name.touched) {
                    <p class="mt-1 text-xs text-rose-400">Indiquez votre nom pour valider votre réponse.</p>
                  }
                </div>
                <div>
                  <label class="mb-1.5 block text-sm text-slate-400" for="pq-comment">Commentaire (optionnel)</label>
                  <textarea
                    id="pq-comment"
                    formControlName="comment"
                    rows="3"
                    class="w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-white focus:border-cyan-500/50 focus:outline-none"
                  ></textarea>
                </div>
                <div class="flex flex-wrap gap-2">
                  <button
                    type="button"
                    class="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-emerald-500 disabled:opacity-50"
                    [disabled]="busy()"
                    (click)="decide('accept')"
                  >
                    <app-lucide-icon name="circle-check" [size]="18" />
                    J'accepte ce devis
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-2 rounded-lg border border-rose-500/40 px-5 py-2.5 text-sm font-medium text-rose-300 hover:bg-rose-950/40 disabled:opacity-50"
                    [disabled]="busy()"
                    (click)="decide('reject')"
                  >
                    <app-lucide-icon name="x" [size]="18" />
                    Je refuse
                  </button>
                </div>
              </form>
            } @else if (q.status === 'ACCEPTED' || q.status === 'CONVERTED') {
              <div class="flex items-start gap-3">
                <app-lucide-icon name="circle-check" [size]="28" class="mt-0.5 text-emerald-400" />
                <div>
                  <h2 class="text-lg font-semibold text-white">Devis accepté</h2>
                  <p class="mt-1 text-sm text-slate-400">
                    @if (q.decidedAt) {
                      Accepté le {{ q.decidedAt | date: 'short' }}
                      @if (q.decidedBy) {
                        par {{ q.decidedBy }}
                      }.
                    }
                    Merci pour votre confiance. {{ q.issuerName }} revient vers vous pour la suite.
                  </p>
                </div>
              </div>
            } @else if (q.status === 'REJECTED') {
              <h2 class="text-lg font-semibold text-white">Devis refusé</h2>
              <p class="mt-1 text-sm text-slate-400">
                Votre réponse a bien été transmise à {{ q.issuerName }}.
              </p>
            } @else if (q.expired) {
              <h2 class="text-lg font-semibold text-white">Devis expiré</h2>
              <p class="mt-1 text-sm text-slate-400">
                La date de validité est dépassée. Contactez {{ q.issuerName }}
                @if (q.issuerEmail) {
                  (<a class="text-cyan-400 hover:underline" [href]="'mailto:' + q.issuerEmail">{{ q.issuerEmail }}</a>)
                }
                pour obtenir un devis actualisé.
              </p>
            } @else {
              <p class="text-sm text-slate-400">Ce devis n'attend plus de réponse de votre part.</p>
            }
          </section>

          <p class="text-center text-xs text-slate-600">
            Document émis par {{ q.issuerName }}
            @if (q.issuerEmail) {
              · {{ q.issuerEmail }}
            }
          </p>
        }
      </div>
    </div>
  `,
})
export class PublicQuotationPageComponent implements OnInit {
  private readonly api = inject(PublicQuotationApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly downloading = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly quotation = signal<PublicQuotation | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    comment: ['', [Validators.maxLength(1024)]],
  });

  readonly statusLabel = computed(() => {
    const q = this.quotation();
    if (!q) {
      return '';
    }
    if (q.expired && q.status === 'SENT') {
      return 'Expiré';
    }
    return QUOTATION_STATUS_LABELS[q.status];
  });

  private token = '';

  ngOnInit(): void {
    this.token = this.route.snapshot.paramMap.get('token') ?? '';
    if (!this.token) {
      this.loadError.set('Lien incomplet.');
      this.loading.set(false);
      return;
    }
    this.api.view(this.token).subscribe({
      next: (q) => {
        this.quotation.set(q);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(apiErrorMessage(err, "Ce lien n'est plus valide."));
        this.loading.set(false);
      },
    });
  }

  statusClasses(): string {
    const q = this.quotation();
    switch (q?.status) {
      case 'ACCEPTED':
      case 'CONVERTED':
        return 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300';
      case 'REJECTED':
        return 'border-rose-500/30 bg-rose-500/10 text-rose-300';
      default:
        return q?.expired
          ? 'border-amber-500/30 bg-amber-500/10 text-amber-300'
          : 'border-cyan-500/30 bg-cyan-500/10 text-cyan-300';
    }
  }

  money(v: number): string {
    return formatMoney(v, this.quotation()?.currencyCode ?? 'EUR');
  }

  decide(decision: Decision): void {
    if (this.busy()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (decision === 'reject' && !window.confirm('Confirmer le refus de ce devis ?')) {
      return;
    }
    const { name, comment } = this.form.getRawValue();
    const trimmedComment = comment.trim() || null;
    this.busy.set(true);
    this.actionError.set(null);
    const req =
      decision === 'accept'
        ? this.api.accept(this.token, name.trim(), trimmedComment)
        : this.api.reject(this.token, name.trim(), trimmedComment);
    req.subscribe({
      next: (q) => {
        this.quotation.set(q);
        this.busy.set(false);
      },
      error: (err) => {
        this.actionError.set(apiErrorMessage(err, 'Votre réponse n’a pas pu être enregistrée.'));
        this.busy.set(false);
      },
    });
  }

  downloadPdf(): void {
    const q = this.quotation();
    if (!q || this.downloading()) {
      return;
    }
    this.downloading.set(true);
    this.api.pdf(this.token).subscribe({
      next: (blob) => {
        const pdfBlob = blob.type === 'application/pdf' ? blob : new Blob([blob], { type: 'application/pdf' });
        const url = URL.createObjectURL(pdfBlob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `devis-${q.number}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
        this.downloading.set(false);
      },
      error: (err) => {
        this.actionError.set(apiErrorMessage(err, 'Téléchargement impossible'));
        this.downloading.set(false);
      },
    });
  }
}
