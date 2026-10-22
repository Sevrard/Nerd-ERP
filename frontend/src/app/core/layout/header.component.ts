import { Component, inject } from '@angular/core';
import { LayoutUiService } from './layout-ui.service';
import { WorkspaceService } from '../workspace/workspace.service';

@Component({
  selector: 'app-header',
  standalone: true,
  template: `
    <header
      class="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-800 bg-slate-900/80 px-4 backdrop-blur md:px-8"
    >
      <div class="flex items-center gap-3 min-w-0">
        <button
          type="button"
          class="rounded-lg border border-slate-700 px-2 py-1 text-xs text-slate-300 md:hidden"
          (click)="layout.toggleMobileSidebar()"
        >
          Menu
        </button>
        <div class="min-w-0">
          <h1 class="truncate text-base font-semibold text-white">
            {{ workspaces.selectedWorkspace()?.name ?? 'Workspace' }}
          </h1>
          <p class="text-xs text-slate-500 truncate">Données filtrées par workspace actif</p>
        </div>
        @if (workspaces.selectedWorkspace()?.active) {
          <span class="hidden sm:inline text-xs rounded-full border border-brand-500/20 bg-brand-950 px-2 py-0.5 text-brand-400"
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
