import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { ApiClient } from '../../core/api/api-client.service';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { MonthlyRevenuePoint } from '../../shared/models/dashboard.model';
import { ProductSalesStat } from '../../shared/models/product-sales.model';
import { formatMoney } from '../../shared/utils/money.util';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { DashboardRevenueChartComponent } from './dashboard-revenue-chart.component';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

const TOP_N = 5;

@Component({
  selector: 'app-dashboard-product-stats',
  standalone: true,
  imports: [DecimalPipe, ErrorBannerComponent, LoadingStateComponent, DashboardRevenueChartComponent],
  template: `
    @if (error()) {
      <app-error-banner [message]="error()!" />
    }
    @if (loading()) {
      <app-loading-state />
    } @else {
      <div class="grid gap-6 xl:grid-cols-2">
        <app-dashboard-revenue-chart [points]="trendPoints()" [periodLabel]="trendPeriodLabel()" />

        <article class="rounded-2xl border border-slate-800/90 bg-slate-900/70 p-5 sm:p-6">
          <h3 class="text-base font-semibold text-white">Répartition des ventes</h3>
          <p class="mt-0.5 text-xs text-slate-500">Part du CA TTC par produit (top {{ topN }})</p>

          @if (!donutSegments().length) {
            <p class="mt-10 text-center text-sm text-slate-500">Aucune vente sur la période.</p>
          } @else {
            <div class="mt-6 flex flex-col items-center gap-6 sm:flex-row sm:items-center sm:justify-center">
              <div
                class="relative size-40 shrink-0 rounded-full shadow-inner ring-1 ring-slate-700/80"
                [style.background]="donutGradient()"
                role="img"
                aria-label="Répartition des ventes"
              >
                <div class="absolute inset-[20%] rounded-full bg-slate-950"></div>
              </div>
              <ul class="grid w-full max-w-xs gap-2.5">
                @for (seg of donutSegments(); track seg.label) {
                  <li class="flex items-center gap-2 text-sm">
                    <span class="size-2.5 shrink-0 rounded-full" [style.background]="seg.color"></span>
                    <span class="min-w-0 flex-1 truncate text-slate-300" [title]="seg.label">{{ seg.label }}</span>
                  </li>
                }
              </ul>
            </div>
          }
        </article>

        <article class="rounded-2xl border border-slate-800/90 bg-slate-900/70 p-5 sm:p-6 lg:col-span-2 lg:mt-0">
          <div class="flex flex-wrap items-end justify-between gap-3">
            <div>
              <h3 class="text-base font-semibold text-white">Palmarès Ventes par Produit</h3>
              <p class="mt-0.5 text-xs text-slate-500">
                Lignes de factures émises, payées ou partiellement payées.
              </p>
            </div>
          </div>

          @if (!rows().length) {
            <p class="mt-8 text-center text-sm text-slate-500">Aucune vente produit sur cette période.</p>
          } @else {
            <div class="mt-5 overflow-x-auto">
              <table class="w-full min-w-[640px] text-left text-sm">
                <thead>
                  <tr class="border-b border-slate-800 text-[11px] font-medium uppercase tracking-wide text-slate-500">
                    <th class="pb-3 pr-4 font-medium">Produit</th>
                    <th class="pb-3 pr-4 font-medium">Réf.</th>
                    <th class="pb-3 pr-4 text-right font-medium">Quantité</th>
                    <th class="pb-3 pr-4 text-right font-medium">CA généré</th>
                    <th class="pb-3 font-medium">% du total</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-800/80">
                  @for (row of topRows(); track trackRow(row)) {
                    <tr class="text-slate-200">
                      <td class="py-3.5 pr-4">
                        <div class="flex items-center gap-3">
                          <span class="size-9 shrink-0 rounded-lg bg-slate-800 ring-1 ring-slate-700/60"></span>
                          <span class="max-w-[220px] truncate font-medium text-white" [title]="row.productName">{{
                            row.productName
                          }}</span>
                        </div>
                      </td>
                      <td class="py-3.5 pr-4 font-mono text-xs text-slate-400">{{ row.productReferenceCode || '—' }}</td>
                      <td class="py-3.5 pr-4 text-right tabular-nums">{{ row.quantitySold | number: '1.0-0' }}</td>
                      <td class="py-3.5 pr-4 text-right font-medium tabular-nums text-white">{{ money(row.revenueTtc) }}</td>
                      <td class="py-3.5">
                        <div class="flex items-center gap-3">
                          <span class="w-10 shrink-0 text-right text-xs tabular-nums text-slate-400">{{
                            sharePct(row.revenueTtc) | number: '1.1-1'
                          }}%</span>
                          <div class="h-1.5 min-w-[80px] flex-1 overflow-hidden rounded-full bg-slate-800">
                            <div
                              class="h-full rounded-full bg-violet-500/90"
                              [style.width.%]="sharePct(row.revenueTtc)"
                            ></div>
                          </div>
                        </div>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </article>
      </div>
    }
  `,
})
export class DashboardProductStatsComponent {
  private readonly api = inject(ApiClient);
  private readonly workspaces = inject(WorkspaceService);

  readonly from = input<string | null>(null);
  readonly to = input<string | null>(null);
  readonly allPeriods = input(false);
  readonly refreshToken = input(0);
  readonly trendPoints = input<MonthlyRevenuePoint[]>([]);
  readonly trendPeriodLabel = input('Même période que les KPI');

  readonly topN = TOP_N;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly rows = signal<ProductSalesStat[]>([]);

  readonly topRows = computed(() => [...this.rows()].slice(0, TOP_N));
  readonly totalRevenue = computed(() => this.rows().reduce((s, r) => s + r.revenueTtc, 0));

  private readonly palette = ['#a855f7', '#22c55e', '#eab308', '#6366f1', '#14b8a6', '#64748b'];

  readonly donutSegments = computed(() => {
    const total = this.totalRevenue();
    if (total <= 0) {
      return [] as { label: string; color: string }[];
    }
    const top = this.topRows();
    const segments = top.map((r, i) => ({
      label: r.productName,
      color: this.palette[i % this.palette.length],
    }));
    const topSum = top.reduce((s, r) => s + r.revenueTtc, 0);
    if (total - topSum > 0.01) {
      segments.push({ label: 'Autres', color: '#334155' });
    }
    return segments;
  });

  readonly donutGradient = computed(() => {
    const total = this.totalRevenue();
    const top = this.topRows();
    if (total <= 0 || !top.length) {
      return 'conic-gradient(#334155 0deg 360deg)';
    }
    let angle = 0;
    const parts: string[] = [];
    top.forEach((r, i) => {
      const sweep = (r.revenueTtc / total) * 360;
      const start = angle;
      angle += sweep;
      parts.push(`${this.palette[i % this.palette.length]} ${start}deg ${angle}deg`);
    });
    const rest = total - top.reduce((s, r) => s + r.revenueTtc, 0);
    if (rest > 0.01) {
      parts.push(`#334155 ${angle}deg 360deg`);
    }
    return `conic-gradient(${parts.join(', ')})`;
  });

  constructor() {
    effect(() => {
      this.workspaces.selectedId();
      this.workspaces.generation();
      this.from();
      this.to();
      this.allPeriods();
      this.refreshToken();
      this.load();
    });
  }

  money(v: number): string {
    return formatMoney(v);
  }

  trackRow(row: ProductSalesStat): string {
    return row.productId ?? `unlinked-${row.productName}`;
  }

  sharePct(revenue: number): number {
    const total = this.totalRevenue();
    return total > 0 ? (revenue / total) * 100 : 0;
  }

  private load(): void {
    const workspaceId = this.workspaces.selectedId();
    if (!workspaceId) {
      return;
    }
    const gen = this.workspaces.generation();
    const all = this.allPeriods();
    this.loading.set(true);
    this.error.set(null);
    this.api
      .get<ProductSalesStat[]>('/dashboard/product-sales', {
        workspaceId,
        from: all ? undefined : this.from() ?? undefined,
        to: all ? undefined : this.to() ?? undefined,
        allPeriods: all,
      })
      .subscribe({
        next: (data) => {
          if (this.workspaces.isStale(gen)) {
            return;
          }
          this.rows.set(
            data.map((r) => ({
              ...r,
              quantitySold: Number(r.quantitySold),
              revenueHt: Number(r.revenueHt),
              revenueTtc: Number(r.revenueTtc),
            })),
          );
          this.loading.set(false);
        },
        error: (err) => {
          if (this.workspaces.isStale(gen)) {
            return;
          }
          this.error.set(apiErrorMessage(err, 'Impossible de charger les stats produits'));
          this.loading.set(false);
        },
      });
  }
}
