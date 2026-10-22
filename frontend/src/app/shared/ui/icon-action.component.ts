import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideIconComponent, LucideIconName } from './lucide-icon.component';

export type IconActionVariant = 'default' | 'brand' | 'danger';

@Component({
  selector: 'app-icon-action',
  standalone: true,
  imports: [RouterLink, LucideIconComponent],
  template: `
    @if (link(); as route) {
      <a
        [routerLink]="route"
        [class]="classes()"
        [title]="label()"
        [attr.aria-label]="label()"
      >
        <app-lucide-icon [name]="displayIcon()" [size]="16" [spin]="loading()" />
      </a>
    } @else {
      <button
        type="button"
        [class]="classes()"
        [title]="label()"
        [attr.aria-label]="label()"
        [disabled]="disabled() || loading()"
        (click)="action.emit()"
      >
        <app-lucide-icon [name]="displayIcon()" [size]="16" [spin]="loading()" />
      </button>
    }
  `,
})
export class IconActionComponent {
  readonly icon = input.required<LucideIconName>();
  readonly label = input.required<string>();
  readonly variant = input<IconActionVariant>('default');
  readonly link = input<unknown[] | string | null>(null);
  readonly disabled = input(false);
  readonly loading = input(false);
  readonly action = output<void>();

  displayIcon(): LucideIconName {
    return this.loading() ? 'loader' : this.icon();
  }

  classes(): string {
    const base =
      'inline-flex size-8 items-center justify-center rounded-lg border transition focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/50 disabled:pointer-events-none disabled:opacity-40';
    switch (this.variant()) {
      case 'brand':
        return `${base} border-slate-700/80 text-brand-400 hover:border-slate-600 hover:bg-slate-800 hover:text-brand-300`;
      case 'danger':
        return `${base} border-transparent text-red-400 hover:bg-red-950/50 hover:text-red-300`;
      default:
        return `${base} border-slate-700/80 text-slate-400 hover:border-slate-600 hover:bg-slate-800 hover:text-white`;
    }
  }
}
