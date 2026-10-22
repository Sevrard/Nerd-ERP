import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-error-banner',
  standalone: true,
  template: `
    <div class="rounded-lg border border-red-500/30 bg-red-950/40 px-4 py-3 text-sm text-red-200" role="alert">
      {{ message }}
    </div>
  `,
})
export class ErrorBannerComponent {
  @Input({ required: true }) message!: string;
}
