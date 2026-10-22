import { Component, computed, input } from '@angular/core';
import { MonthlyRevenuePoint } from '../../shared/models/dashboard.model';
import { formatMoney } from '../../shared/utils/money.util';

@Component({
  selector: 'app-dashboard-revenue-chart',
  standalone: true,
  imports: [],
  template: `
    <article class="flex h-full min-h-[320px] flex-col rounded-2xl border border-slate-800/90 bg-slate-900/70 p-5 sm:p-6">
      <h3 class="text-base font-semibold text-white">Evolution CA Facturé vs Encaissé</h3>
      <p class="mt-0.5 text-xs text-slate-500">{{ subtitle() }}</p>

      @if (!points().length) {
        <p class="mt-auto py-12 text-center text-sm text-slate-500">Pas encore de données sur cette période.</p>
      } @else if (isEmpty()) {
        <p class="mt-auto py-12 text-center text-sm text-slate-500">
          Aucun CA sur les mois affichés — élargissez la période ou vérifiez les dates de facturation.
        </p>
      } @else {
        <div class="mt-4 flex min-h-0 flex-1 flex-col">
          <div class="flex min-h-[220px] flex-1 gap-3">
            <div class="flex w-14 shrink-0 flex-col justify-between py-1 text-[10px] tabular-nums text-slate-500">
              <span>{{ yMaxLabel() }}</span>
              <span>{{ yMidLabel() }}</span>
              <span>0 €</span>
            </div>
            <div class="relative min-w-0 flex-1">
              <svg
                class="h-full w-full min-h-[200px]"
                [attr.viewBox]="'0 0 ' + svgWidth() + ' ' + height"
                preserveAspectRatio="xMidYMid meet"
              >
                @for (y of gridLines(); track y) {
                  <line
                    [attr.x1]="padLeft"
                    [attr.x2]="svgWidth() - padRight"
                    [attr.y1]="y"
                    [attr.y2]="y"
                    stroke="#334155"
                    stroke-width="1"
                    stroke-dasharray="4 4"
                  />
                }
                @for (bar of layout(); track bar.label) {
                  <rect
                    [attr.x]="bar.barX"
                    [attr.y]="bar.barY"
                    [attr.width]="bar.barW"
                    [attr.height]="bar.barH"
                    rx="4"
                    class="fill-teal-500/90"
                  />
                }
                @if (linePath()) {
                  <polyline
                    [attr.points]="linePath()"
                    fill="none"
                    stroke="#a855f7"
                    stroke-width="2.5"
                    stroke-linejoin="round"
                    stroke-linecap="round"
                    vector-effect="non-scaling-stroke"
                  />
                }
                @for (dot of layout(); track dot.label + '-dot') {
                  <circle [attr.cx]="dot.dotX" [attr.cy]="dot.dotY" r="4.5" fill="#a855f7" stroke="#0f172a" stroke-width="2" />
                }
              </svg>
            </div>
          </div>
          <div
            class="ml-[3.75rem] mt-2 grid gap-1"
            [style.gridTemplateColumns]="'repeat(' + points().length + ', minmax(0, 1fr))'"
          >
            @for (p of points(); track p.month) {
              <span class="truncate text-center text-[11px] text-slate-500">{{ p.label }}</span>
            }
          </div>
        </div>

        <div class="mt-4 flex flex-wrap gap-4 text-xs text-slate-400">
          <span class="inline-flex items-center gap-2">
            <span class="size-2.5 rounded-full bg-violet-500"></span>
            CA facturé
          </span>
          <span class="inline-flex items-center gap-2">
            <span class="size-2.5 rounded-sm bg-teal-500"></span>
            CA encaissé
          </span>
        </div>
      }
    </article>
  `,
})
export class DashboardRevenueChartComponent {
  readonly points = input<MonthlyRevenuePoint[]>([]);
  readonly periodLabel = input<string>('Même période que les KPI');

  readonly height = 200;
  readonly padLeft = 8;
  readonly padRight = 8;
  readonly padTop = 12;
  readonly padBottom = 8;

  readonly subtitle = computed(() => this.periodLabel());

  readonly isEmpty = computed(() => {
    const pts = this.points();
    return pts.length > 0 && pts.every((p) => p.invoicedRevenue <= 0 && p.collectedAmount <= 0);
  });

  readonly svgWidth = computed(() => Math.max(360, this.points().length * 56));

  readonly yMax = computed(() => {
    const vals = this.points().flatMap((p) => [p.invoicedRevenue, p.collectedAmount]);
    const max = Math.max(...vals, 0);
    if (max <= 0) {
      return 100;
    }
    const step = max <= 300 ? 50 : max <= 1000 ? 200 : max <= 5000 ? 500 : 1000;
    return Math.ceil(max / step) * step;
  });

  readonly yMaxLabel = computed(() => formatMoney(this.yMax()));
  readonly yMidLabel = computed(() => formatMoney(this.yMax() / 2));

  readonly gridLines = computed(() => {
    const chartH = this.height - this.padTop - this.padBottom;
    const y0 = this.padTop + chartH;
    const yMid = this.padTop + chartH / 2;
    return [this.padTop, yMid, y0];
  });

  readonly layout = computed(() => {
    const pts = this.points();
    const max = this.yMax();
    const chartH = this.height - this.padTop - this.padBottom;
    const innerW = this.svgWidth() - this.padLeft - this.padRight;
    const slot = innerW / Math.max(pts.length, 1);
    const barW = Math.min(32, slot * 0.42);

    return pts.map((p, i) => {
      const centerX = this.padLeft + slot * i + slot / 2;
      const barH = max > 0 ? (p.collectedAmount / max) * chartH : 0;
      const barY = this.padTop + chartH - barH;
      const dotY = max > 0 ? this.padTop + chartH - (p.invoicedRevenue / max) * chartH : this.padTop + chartH;
      return {
        label: p.label,
        barX: centerX - barW / 2,
        barY,
        barW,
        barH: Math.max(barH, p.collectedAmount > 0 ? 3 : 0),
        dotX: centerX,
        dotY,
      };
    });
  });

  readonly linePath = computed(() =>
    this.layout()
      .map((l) => `${l.dotX},${l.dotY}`)
      .join(' '),
  );
}
