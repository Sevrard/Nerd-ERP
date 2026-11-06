import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { LucideIconComponent, LucideIconName } from '../../shared/ui/lucide-icon.component';
import { LayoutUiService } from './layout-ui.service';

interface MobileTab {
  path: string;
  label: string;
  icon: LucideIconName;
}

const TABS: MobileTab[] = [
  { path: '/dashboard', label: 'Accueil', icon: 'house' },
  { path: '/customers', label: 'Clients', icon: 'users' },
  { path: '/quotations', label: 'Devis', icon: 'file-text' },
  { path: '/invoices', label: 'Factures', icon: 'receipt' },
];

/**
 * Barre d'onglets fixée en bas sur smartphone (usage terrain, une main) : accès direct aux écrans
 * du quotidien + bouton « Menu » qui ouvre le panneau latéral complet (workspaces, modules, réglages).
 */
@Component({
  selector: 'app-mobile-nav',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, LucideIconComponent],
  template: `
    <nav
      class="pb-safe fixed inset-x-0 bottom-0 z-30 border-t border-slate-800 bg-[#0B0E11]/95 backdrop-blur md:hidden"
      aria-label="Navigation mobile"
    >
      <ul class="grid grid-cols-5">
        @for (tab of tabs; track tab.path) {
          <li>
            <a
              [routerLink]="tab.path"
              routerLinkActive="text-brand-400"
              #rla="routerLinkActive"
              class="flex min-h-14 flex-col items-center justify-center gap-1 text-[11px] font-medium text-slate-400 transition active:bg-slate-800/60"
              [attr.aria-current]="rla.isActive ? 'page' : null"
              (click)="layout.closeMobileSidebar()"
            >
              <app-lucide-icon [name]="tab.icon" [size]="22" />
              {{ tab.label }}
            </a>
          </li>
        }
        <li>
          <button
            type="button"
            class="flex min-h-14 w-full flex-col items-center justify-center gap-1 text-[11px] font-medium text-slate-400 transition active:bg-slate-800/60"
            [class.text-white]="layout.mobileSidebarOpen()"
            [attr.aria-expanded]="layout.mobileSidebarOpen()"
            aria-label="Ouvrir le menu complet"
            (click)="layout.toggleMobileSidebar()"
          >
            <app-lucide-icon name="menu" [size]="22" />
            Menu
          </button>
        </li>
      </ul>
    </nav>
  `,
})
export class MobileNavComponent {
  readonly layout = inject(LayoutUiService);
  readonly tabs = TABS;
}
