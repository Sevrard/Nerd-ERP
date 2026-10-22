import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { Customer, CustomerHistory, customerDisplayName } from '../../shared/models/customer.model';
import { formatMoney } from '../../shared/utils/money.util';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { IconActionComponent } from '../../shared/ui/icon-action.component';
import { CustomersApiService } from './customers-api.service';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

@Component({
  selector: 'app-customers-detail-page',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, DatePipe, ErrorBannerComponent, LoadingStateComponent, IconActionComponent],
  template: `
    @if (loading()) {
      <app-loading-state />
    } @else if (customer()) {
      <div class="space-y-8">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div>
            <a routerLink="/customers" class="text-sm text-slate-400 hover:text-white">← Clients</a>
            <h1 class="mt-2 text-2xl font-bold text-white">{{ name() }}</h1>
            <p class="text-sm text-slate-400">{{ customer()!.email ?? '—' }} · {{ customer()!.status }}</p>
          </div>
          <div class="flex items-center gap-1">
            <app-icon-action
              icon="pencil"
              label="Modifier le client"
              variant="brand"
              [link]="['/customers', customer()!.id, 'edit']"
            />
            <app-icon-action icon="archive" label="Archiver le client" variant="default" (action)="archive()" />
            <app-icon-action
              icon="trash"
              label="Supprimer le client"
              variant="danger"
              [loading]="deleting()"
              (action)="confirmDelete()"
            />
          </div>
        </div>

        @if (error()) {
          <app-error-banner [message]="error()!" />
        }

        <section class="rounded-xl border border-slate-800 bg-slate-900/50 p-6">
          <h2 class="mb-4 text-lg font-semibold text-white">Planifier un suivi</h2>
          <form class="grid gap-4 sm:grid-cols-2" [formGroup]="followUp" (ngSubmit)="submitFollowUp()">
            <div>
              <label class="mb-1 block text-xs text-slate-500">Date / heure</label>
              <input
                type="datetime-local"
                formControlName="scheduledAtLocal"
                class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
              />
            </div>
            <div>
              <label class="mb-1 block text-xs text-slate-500">Type d’action</label>
              <input
                formControlName="actionType"
                class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
              />
            </div>
            <div class="sm:col-span-2">
              <label class="mb-1 block text-xs text-slate-500">Notes</label>
              <textarea formControlName="notes" rows="2" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"></textarea>
            </div>
            <button
              type="submit"
              class="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500 sm:col-span-2 sm:w-fit"
              [disabled]="followUp.invalid"
            >
              Enregistrer le suivi
            </button>
          </form>
        </section>

        @if (history()) {
          <section class="space-y-4">
            <h2 class="text-lg font-semibold text-white">Historique</h2>
            <div class="grid gap-4 lg:grid-cols-2">
              <div class="rounded-xl border border-slate-800 p-4">
                <h3 class="mb-2 text-sm font-medium text-slate-400">Suivis</h3>
                <ul class="space-y-2 text-sm">
                  @for (f of history()!.followUps; track f.id) {
                    <li class="text-slate-300">
                      {{ f.actionType }} — {{ f.scheduledAt | date: 'short' }}
                      @if (f.completed) {
                        <span class="text-brand-400">(fait)</span>
                      }
                    </li>
                  }
                </ul>
              </div>
              <div class="rounded-xl border border-slate-800 p-4">
                <h3 class="mb-2 text-sm font-medium text-slate-400">Devis</h3>
                <ul class="space-y-2 text-sm text-slate-300">
                  @for (q of history()!.quotations; track q.id) {
                    <li>{{ q.number ?? q.id.slice(0, 8) }} · {{ q.status }} · {{ money(q.totalTtc) }}</li>
                  }
                </ul>
              </div>
              <div class="rounded-xl border border-slate-800 p-4">
                <h3 class="mb-2 text-sm font-medium text-slate-400">Factures</h3>
                <ul class="space-y-2 text-sm text-slate-300">
                  @for (inv of history()!.invoices; track inv.id) {
                    <li>{{ inv.number }} · {{ inv.status }} · {{ money(inv.totalTtc) }}</li>
                  }
                </ul>
              </div>
              <div class="rounded-xl border border-slate-800 p-4">
                <h3 class="mb-2 text-sm font-medium text-slate-400">Paiements</h3>
                <ul class="space-y-2 text-sm text-slate-300">
                  @for (p of history()!.payments; track p.id) {
                    <li>{{ p.paymentDate | date: 'shortDate' }} · {{ money(p.amount) }}</li>
                  }
                </ul>
              </div>
            </div>
          </section>
        }
      </div>
    }
  `,
})
export class CustomersDetailPageComponent implements OnInit {
  private readonly api = inject(CustomersApiService);
  private readonly workspaces = inject(WorkspaceService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(true);
  readonly deleting = signal(false);
  readonly error = signal<string | null>(null);
  readonly customer = signal<Customer | null>(null);
  readonly history = signal<CustomerHistory | null>(null);

  readonly followUp = this.fb.nonNullable.group({
    scheduledAtLocal: ['', Validators.required],
    actionType: ['CALL', Validators.required],
    notes: [''],
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.api.get(id).subscribe({
      next: (c) => {
        this.customer.set(c);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Client introuvable'));
        this.loading.set(false);
      },
    });
    this.api.history(id).subscribe({
      next: (h) => this.history.set(h),
      error: () => undefined,
    });
  }

  name(): string {
    const c = this.customer();
    return c ? customerDisplayName(c) : '';
  }

  money(n: number): string {
    return formatMoney(n);
  }

  archive(): void {
    const c = this.customer();
    if (!c) {
      return;
    }
    this.api.archive(c.id).subscribe({
      next: (updated) => this.customer.set(updated),
      error: (err) => this.error.set(apiErrorMessage(err, 'Archivage impossible')),
    });
  }

  confirmDelete(): void {
    const c = this.customer();
    if (!c) {
      return;
    }
    const ok = confirm(
      `Supprimer définitivement ${this.name()} ?\n\nImpossible si des devis, factures, paiements ou contrats de maintenance existent.`,
    );
    if (!ok) {
      return;
    }
    this.error.set(null);
    this.deleting.set(true);
    this.api.delete(c.id).subscribe({
      next: () => this.router.navigate(['/customers']),
      error: (err) => {
        this.deleting.set(false);
        this.error.set(apiErrorMessage(err, 'Suppression impossible'));
      },
    });
  }

  submitFollowUp(): void {
    const c = this.customer();
    if (!c || this.followUp.invalid) {
      return;
    }
    const { scheduledAtLocal, actionType, notes } = this.followUp.getRawValue();
    const scheduledAt = new Date(scheduledAtLocal).toISOString();
    this.api
      .scheduleFollowUp(c.id, {
        workspaceId: this.workspaces.requireSelectedId(),
        scheduledAt,
        actionType,
        notes: notes || null,
      })
      .subscribe({
        next: () => {
          this.followUp.reset({ actionType: 'CALL', scheduledAtLocal: '', notes: '' });
          this.api.history(c.id).subscribe((h) => this.history.set(h));
        },
        error: (err) => this.error.set(apiErrorMessage(err, 'Suivi impossible')),
      });
  }
}
