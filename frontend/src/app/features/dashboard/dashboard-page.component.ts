import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ApiClient } from '../../core/api/api-client.service';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { DashboardSummary, RevenueTrendResponse } from '../../shared/models/dashboard.model';
import { formatMoney } from '../../shared/utils/money.util';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { LucideIconComponent, LucideIconName } from '../../shared/ui/lucide-icon.component';
import { EtsySyncPanelComponent } from './etsy-sync-panel.component';
import { DashboardProductStatsComponent } from './dashboard-product-stats.component';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

interface KpiCard {
  label: string;
  value: string;
  hint: string;
  icon: LucideIconName;
  iconBg: string;
  iconColor: string;
}

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    ErrorBannerComponent,
    LoadingStateComponent,
    LucideIconComponent,
    EtsySyncPanelComponent,
    DashboardProductStatsComponent,
  ],
  template: `
    <div class="w-full space-y-6">
      @if (isNerdForge()) {
        <app-etsy-sync-panel (syncCompleted)="onEtsySyncCompleted()" />
      }

      <div class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 class="text-2xl font-bold tracking-tight text-white">Dashboard</h1>
          <p class="text-sm text-slate-400">Indicateurs du workspace · période personnalisable</p>
        </div>
        <form class="flex flex-wrap items-end gap-2" [formGroup]="period" (ngSubmit)="reload()">
          <div>
            <label class="mb-1 block text-[10px] uppercase tracking-wide text-slate-600" for="from">Du</label>
            <input
              id="from"
              type="date"
              formControlName="from"
              class="rounded-lg border border-slate-700 bg-slate-900 px-2.5 py-1.5 text-sm text-white"
            />
          </div>
          <div>
            <label class="mb-1 block text-[10px] uppercase tracking-wide text-slate-600" for="to">Au</label>
            <input
              id="to"
              type="date"
              formControlName="to"
              class="rounded-lg border border-slate-700 bg-slate-900 px-2.5 py-1.5 text-sm text-white"
            />
          </div>
          <button
            type="submit"
            class="rounded-lg bg-slate-700 px-3 py-1.5 text-sm text-white hover:bg-slate-600"
            (click)="useCustomPeriod()"
          >
            Appliquer
          </button>
          <button
            type="button"
            class="rounded-lg border border-slate-600 px-3 py-1.5 text-sm text-slate-300 hover:bg-slate-800"
            (click)="loadAllPeriods()"
          >
            Tout
          </button>
        </form>
      </div>

      @if (error()) {
        <app-error-banner [message]="error()!" />
      }
      @if (loading()) {
        <app-loading-state />
      } @else if (summary()) {
        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 2xl:grid-cols-4">
          @for (card of cards(); track card.label) {
            <article
              class="relative overflow-hidden rounded-2xl border border-slate-800/90 bg-gradient-to-br from-slate-900/90 to-slate-950/90 p-5 shadow-sm shadow-black/20"
            >
              <div
                class="absolute right-4 top-4 flex size-10 items-center justify-center rounded-xl"
                [class]="card.iconBg"
              >
                <span [class]="card.iconColor">
                  <app-lucide-icon [name]="card.icon" [size]="20" />
                </span>
              </div>
              <p class="pr-12 text-[11px] font-semibold uppercase tracking-wide text-slate-500">{{ card.label }}</p>
              <p class="mt-2 text-2xl font-bold tabular-nums text-white">{{ card.value }}</p>
              <p class="mt-1 text-xs text-slate-500">{{ card.hint }}</p>
            </article>
          }
        </div>

        <app-dashboard-product-stats
          [from]="period.get('from')!.value"
          [to]="period.get('to')!.value"
          [allPeriods]="allPeriods()"
          [refreshToken]="statsRefresh()"
          [trendPoints]="trendPoints()"
          [trendPeriodLabel]="trendPeriodLabel()"
        />
      }
    </div>
  `,
})
export class DashboardPageComponent {
  private readonly api = inject(ApiClient);
  private readonly workspaces = inject(WorkspaceService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly summary = signal<DashboardSummary | null>(null);
  readonly priorInvoiced = signal<number | null>(null);
  readonly trendPoints = signal<RevenueTrendResponse['points']>([]);
  readonly trendPeriodLabel = signal('6 derniers mois');

  readonly period = this.fb.nonNullable.group({
    from: [this.defaultPeriodFromIso()],
    to: [this.todayIso()],
  });

  readonly cards = signal<KpiCard[]>([]);
  readonly allPeriods = signal(false);
  readonly statsRefresh = signal(0);
  readonly isNerdForge = computed(() => this.workspaces.selectedWorkspace()?.slug === 'nerd-forge');

  constructor() {
    effect(() => {
      this.workspaces.selectedId();
      this.workspaces.generation();
      this.reload();
    });
  }

  reload(): void {
    this.load();
  }

  onEtsySyncCompleted(): void {
    this.statsRefresh.update((n) => n + 1);
    this.load();
  }

  load(): void {
    const workspaceId = this.workspaces.selectedId();
    if (!workspaceId) {
      return;
    }
    const gen = this.workspaces.generation();
    const { from, to } = this.period.getRawValue();
    const all = this.allPeriods();
    this.loading.set(true);
    this.error.set(null);
    this.priorInvoiced.set(null);

    const priorRange = all ? null : this.priorPeriodRange(from, to);

    this.trendPeriodLabel.set(
      all ? '12 derniers mois' : `Du ${from} au ${to} (par mois)`,
    );

    this.api
      .get<RevenueTrendResponse>('/dashboard/revenue-trend', {
        workspaceId,
        months: all ? 12 : 6,
        from: all ? undefined : from,
        to: all ? undefined : to,
        allPeriods: all,
      })
      .subscribe({
        next: (trend) => {
          if (this.workspaces.isStale(gen)) {
            return;
          }
          this.trendPoints.set(
            trend.points.map((p) => ({
              ...p,
              invoicedRevenue: Number(p.invoicedRevenue),
              collectedAmount: Number(p.collectedAmount),
            })),
          );
        },
        error: () => {
          if (!this.workspaces.isStale(gen)) {
            this.trendPoints.set([]);
          }
        },
      });

    if (priorRange) {
      this.api
        .get<DashboardSummary>('/dashboard/summary', {
          workspaceId,
          from: priorRange.from,
          to: priorRange.to,
          allPeriods: false,
        })
        .subscribe({
          next: (data) => {
            if (!this.workspaces.isStale(gen)) {
              this.priorInvoiced.set(Number(data.invoicedRevenue));
              this.rebuildCards(this.summary());
            }
          },
        });
    }

    this.api
      .get<DashboardSummary>('/dashboard/summary', {
        workspaceId,
        from: all ? undefined : from,
        to: all ? undefined : to,
        allPeriods: all,
      })
      .subscribe({
        next: (data) => {
          if (this.workspaces.isStale(gen)) {
            return;
          }
          const normalized: DashboardSummary = {
            ...data,
            invoicedRevenue: Number(data.invoicedRevenue),
            collectedAmount: Number(data.collectedAmount),
            outstandingAmount: Number(data.outstandingAmount),
            unitsSold: Number(data.unitsSold),
            invoiceCountInPeriod: Number(data.invoiceCountInPeriod),
          };
          this.summary.set(normalized);
          this.rebuildCards(normalized);
          this.loading.set(false);
          this.statsRefresh.update((n) => n + 1);
        },
        error: (err) => {
          if (this.workspaces.isStale(gen)) {
            return;
          }
          this.error.set(apiErrorMessage(err, 'Impossible de charger le dashboard'));
          this.loading.set(false);
        },
      });
  }

  loadAllPeriods(): void {
    this.allPeriods.set(true);
    this.load();
  }

  useCustomPeriod(): void {
    this.allPeriods.set(false);
  }

  private rebuildCards(data: DashboardSummary | null): void {
    if (!data) {
      return;
    }
    const invoiced = data.invoicedRevenue;
    const collected = data.collectedAmount;
    const collectedPct = invoiced > 0 ? Math.round((collected / invoiced) * 100) : 0;
    const avgBasket =
      data.invoiceCountInPeriod > 0 ? invoiced / data.invoiceCountInPeriod : 0;
    const prior = this.priorInvoiced();
    let invoicedHint = this.allPeriods() ? 'Toutes périodes' : 'Sur la période sélectionnée';
    if (prior != null && prior > 0) {
      const delta = ((invoiced - prior) / prior) * 100;
      const sign = delta >= 0 ? '+' : '';
      invoicedHint = `${sign}${delta.toFixed(0)} % vs période préc.`;
    } else if (prior === 0 && invoiced > 0) {
      invoicedHint = 'Nouveau CA sur la période';
    }

    this.cards.set([
      {
        label: 'CA facturé',
        value: formatMoney(invoiced),
        hint: invoicedHint,
        icon: 'file-text',
        iconBg: 'bg-blue-500/15',
        iconColor: 'text-blue-400',
      },
      {
        label: 'Encaissé',
        value: formatMoney(collected),
        hint: invoiced > 0 ? `${collectedPct} % du facturé` : '—',
        icon: 'wallet',
        iconBg: 'bg-emerald-500/15',
        iconColor: 'text-emerald-400',
      },
      {
        label: 'Reste à encaisser',
        value: formatMoney(data.outstandingAmount),
        hint: data.outstandingAmount <= 0 ? 'Tout est à jour' : 'Suivi des impayés',
        icon: 'hand-coins',
        iconBg: 'bg-amber-500/15',
        iconColor: 'text-amber-400',
      },
      {
        label: 'Factures en retard',
        value: String(data.overdueInvoices),
        hint: data.overdueInvoices === 0 ? 'Aucun retard détecté' : 'À relancer',
        icon: 'triangle-alert',
        iconBg: 'bg-rose-500/15',
        iconColor: 'text-rose-400',
      },
      {
        label: 'Clients actifs',
        value: String(data.activeClients),
        hint: 'Clients non archivés',
        icon: 'users',
        iconBg: 'bg-sky-500/15',
        iconColor: 'text-sky-400',
      },
      {
        label: 'Prospects actifs',
        value: String(data.activeProspects),
        hint: data.activeProspects === 0 ? '—' : 'Pipeline commercial',
        icon: 'user-plus',
        iconBg: 'bg-teal-500/15',
        iconColor: 'text-teal-400',
      },
      {
        label: 'Panier moyen',
        value: formatMoney(avgBasket),
        hint: data.invoiceCountInPeriod > 0 ? `${data.invoiceCountInPeriod} facture(s)` : '—',
        icon: 'shopping-cart',
        iconBg: 'bg-violet-500/15',
        iconColor: 'text-violet-400',
      },
      {
        label: 'Articles vendus',
        value: String(Math.round(data.unitsSold)),
        hint: this.allPeriods() ? 'Toutes périodes' : 'Sur la période sélec.',
        icon: 'tag',
        iconBg: 'bg-orange-500/15',
        iconColor: 'text-orange-400',
      },
    ]);
  }

  private priorPeriodRange(from: string, to: string): { from: string; to: string } | null {
    const start = new Date(from);
    const end = new Date(to);
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
      return null;
    }
    const days = Math.max(1, Math.round((end.getTime() - start.getTime()) / 86400000) + 1);
    const priorEnd = new Date(start);
    priorEnd.setDate(priorEnd.getDate() - 1);
    const priorStart = new Date(priorEnd);
    priorStart.setDate(priorStart.getDate() - (days - 1));
    return {
      from: priorStart.toISOString().slice(0, 10),
      to: priorEnd.toISOString().slice(0, 10),
    };
  }

  private todayIso(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private defaultPeriodFromIso(): string {
    const d = new Date();
    d.setMonth(d.getMonth() - 12);
    return d.toISOString().slice(0, 10);
  }
}
