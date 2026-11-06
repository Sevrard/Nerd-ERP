import { Component, inject } from '@angular/core';
import { LayoutUiService } from './layout-ui.service';
import { WorkspaceService } from '../workspace/workspace.service';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [LucideIconComponent],
  template: `
    <header
      class="pt-safe sticky top-0 z-30 flex min-h-14 items-center justify-between gap-3 border-b border-slate-800 bg-slate-900/80 px-3 backdrop-blur md:h-16 md:px-8"
    >
      <div class="flex min-w-0 items-center gap-2 md:gap-3">
        <button
          type="button"
          class="inline-flex size-10 shrink-0 items-center justify-center rounded-lg text-slate-300 hover:bg-slate-800 md:hidden"
          aria-label="Ouvrir le menu"
          [attr.aria-expanded]="layout.mobileSidebarOpen()"
          (click)="layout.toggleMobileSidebar()"
        >
          <app-lucide-icon name="menu" [size]="22" />
        </button>
        <div class="min-w-0">
          <h1 class="truncate text-base font-semibold text-white">
            {{ workspaces.selectedWorkspace()?.name ?? 'Workspace' }}
          </h1>
          <p class="hidden truncate text-xs text-slate-500 sm:block">Données filtrées par workspace actif</p>
        </div>
        @if (workspaces.selectedWorkspace()?.active) {
          <span class="hidden rounded-full border border-brand-500/20 bg-brand-950 px-2 py-0.5 text-xs text-brand-400 sm:inline"
            >Actif</span
          >
        }
      </div>
      <div class="flex items-center gap-3">
        <label class="sr-only" for="global-search">Recherche</label>
        <input
          id="global-search"
          type="search"
          placeholder="Rechercher…"
          class="hidden w-48 rounded-lg border border-slate-700 bg-slate-950 px-3 py-1.5 text-sm text-white md:block lg:w-64"
          disabled
          title="Recherche globale — à venir"
        />
      </div>
    </header>
  `,
})
export class HeaderComponent {
  readonly workspaces = inject(WorkspaceService);
  readonly layout = inject(LayoutUiService);
}
