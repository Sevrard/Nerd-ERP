import { Component, computed, input, output } from '@angular/core';
import { PAGE_SIZE_OPTIONS, PageResponse } from '../models/page.model';

type PageMeta = Pick<PageResponse<unknown>, 'page' | 'size' | 'totalElements' | 'totalPages'>;

/**
 * Barre de pagination serveur : « 21–40 sur 134 », navigation, taille de page.
 * Se masque d'elle-même quand il n'y a rien à paginer (0 résultat).
 */
@Component({
  selector: 'app-paginator',
  standalone: true,
  template: `
    @if (meta().totalElements > 0) {
      <nav
        class="flex flex-wrap items-center justify-between gap-3 border-t border-slate-800 px-4 py-3 text-xs text-slate-400"
        aria-label="Pagination"
      >
        <p>
          <span class="font-medium text-slate-200">{{ rangeStart() }}–{{ rangeEnd() }}</span>
          sur {{ meta().totalElements }}
        </p>

        <div class="flex items-center gap-3">
          <label class="flex items-center gap-2">
            <span class="hidden sm:inline">Par page</span>
            <select
              class="rounded-lg border border-slate-700 bg-slate-900 px-2 py-1 text-xs text-slate-200 focus:border-cyan-500 focus:outline-none"
              [value]="meta().size"
              [disabled]="disabled()"
              (change)="onSizeChange($any($event.target).value)"
            >
              @for (opt of sizeOptions(); track opt) {
                <option [value]="opt" [selected]="opt === meta().size">{{ opt }}</option>
              }
            </select>
          </label>

          @if (meta().totalPages > 1) {
            <div class="flex items-center gap-1">
              <button
                type="button"
                class="min-h-9 min-w-9 rounded-lg border border-slate-700/80 px-2 py-1 text-slate-300 transition hover:border-slate-600 hover:bg-slate-800 hover:text-white disabled:pointer-events-none disabled:opacity-40 md:min-h-0 md:min-w-0"
                [disabled]="disabled() || meta().page === 0"
                aria-label="Page précédente"
                (click)="go(meta().page - 1)"
              >
                ‹
              </button>
              @for (p of pages(); track $index) {
                @if (p === null) {
                  <span class="px-1 text-slate-600">…</span>
                } @else {
                  <button
                    type="button"
                    class="min-h-9 min-w-9 rounded-lg border px-2 py-1 transition disabled:pointer-events-none md:min-h-0 md:min-w-8"
                    [class]="
                      p === meta().page
                        ? 'border-cyan-500/60 bg-cyan-500/10 text-cyan-300'
                        : 'border-slate-700/80 text-slate-300 hover:border-slate-600 hover:bg-slate-800 hover:text-white'
                    "
                    [attr.aria-current]="p === meta().page ? 'page' : null"
                    [disabled]="disabled()"
                    (click)="go(p)"
                  >
                    {{ p + 1 }}
                  </button>
                }
              }
              <button
                type="button"
                class="min-h-9 min-w-9 rounded-lg border border-slate-700/80 px-2 py-1 text-slate-300 transition hover:border-slate-600 hover:bg-slate-800 hover:text-white disabled:pointer-events-none disabled:opacity-40 md:min-h-0 md:min-w-0"
                [disabled]="disabled() || meta().page >= meta().totalPages - 1"
                aria-label="Page suivante"
                (click)="go(meta().page + 1)"
              >
                ›
              </button>
            </div>
          }
        </div>
      </nav>
    }
  `,
})
export class PaginatorComponent {
  readonly meta = input.required<PageMeta>();
  readonly disabled = input(false);
  readonly sizeOptions = input<readonly number[]>(PAGE_SIZE_OPTIONS);
  readonly pageChange = output<number>();
  readonly pageSizeChange = output<number>();

  readonly rangeStart = computed(() => this.meta().page * this.meta().size + 1);
  readonly rangeEnd = computed(() =>
    Math.min((this.meta().page + 1) * this.meta().size, this.meta().totalElements),
  );

  /** Pages à afficher : 1 … p-1 p p+1 … N (null = ellipse). */
  readonly pages = computed<(number | null)[]>(() => {
    const total = this.meta().totalPages;
    const current = this.meta().page;
    if (total <= 7) {
      return Array.from({ length: total }, (_, i) => i);
    }
    const set = new Set<number>([0, total - 1, current - 1, current, current + 1]);
    const sorted = [...set].filter((p) => p >= 0 && p < total).sort((a, b) => a - b);
    const out: (number | null)[] = [];
    for (let i = 0; i < sorted.length; i++) {
      if (i > 0 && sorted[i] - sorted[i - 1] > 1) {
        out.push(null);
      }
      out.push(sorted[i]);
    }
    return out;
  });

  go(page: number): void {
    if (page >= 0 && page < this.meta().totalPages && page !== this.meta().page) {
      this.pageChange.emit(page);
    }
  }

  onSizeChange(value: string): void {
    const size = Number(value);
    if (size > 0 && size !== this.meta().size) {
      this.pageSizeChange.emit(size);
    }
  }
}
