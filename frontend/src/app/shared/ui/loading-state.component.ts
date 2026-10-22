import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-loading-state',
  standalone: true,
  template: `<p class="text-sm text-slate-400 py-8 text-center" role="status">{{ label }}</p>`,
})
export class LoadingStateComponent {
  @Input() label = 'Chargement…';
}
