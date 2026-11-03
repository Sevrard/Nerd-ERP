import { Component, input, output } from '@angular/core';
import { SortDirection, sortIndicator } from '../utils/table-sort.util';

@Component({
  selector: 'th[appSortableTh]',
  standalone: true,
  host: {
    class: 'px-4 py-3.5',
    '[class.text-left]': 'align() === "left"',
    '[class.text-right]': 'align() === "right"',
    '[class.text-center]': 'align() === "center"',
  },
  template: `
    <button
      type="button"
      class="inline-flex w-full items-center gap-1 uppercase tracking-wider transition hover:text-cyan-400"
      [class.justify-end]="align() === 'right'"
      [class.justify-center]="align() === 'center'"
      [class.text-cyan-400]="sortKey() === columnKey()"
      (click)="columnSort.emit(columnKey())"
    >
      <span>{{ label() }}</span>
      <span class="text-[10px] normal-case text-cyan-500">{{ indicator() }}</span>
    </button>
  `,
})
export class SortableThComponent {
  readonly label = input.required<string>();
  readonly columnKey = input.required<string>();
  readonly sortKey = input<string | null>(null);
  readonly sortDir = input<SortDirection>('asc');
  readonly align = input<'left' | 'right' | 'center'>('left');
  readonly columnSort = output<string>();

  indicator(): string {
    return sortIndicator(this.sortKey(), this.columnKey(), this.sortDir());
  }
}
