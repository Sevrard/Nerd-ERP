import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  template: `
    <div class="rounded-xl border border-slate-800 bg-slate-900/50 p-10 text-center">
      <p class="text-white font-medium">{{ title }}</p>
      @if (description) {
        <p class="text-sm text-slate-400 mt-2">{{ description }}</p>
      }
    </div>
  `,
})
export class EmptyStateComponent {
  @Input({ required: true }) title!: string;
  @Input() description?: string;
}
