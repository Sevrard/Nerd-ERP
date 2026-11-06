import { Component, inject, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { WorkspaceService } from '../workspace/workspace.service';
import { SidebarComponent } from './sidebar.component';
import { HeaderComponent } from './header.component';
import { MobileNavComponent } from './mobile-nav.component';
import { TermsGateComponent } from './terms-gate.component';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, HeaderComponent, MobileNavComponent, TermsGateComponent],
  template: `
    <div class="flex min-h-screen h-screen max-h-screen overflow-hidden">
      <app-sidebar />
      <div class="flex min-h-0 min-w-0 flex-1 flex-col">
        <app-header />
        <main class="flex-1 overflow-y-auto overflow-x-hidden p-3 pb-24 sm:p-6 md:pb-6 lg:p-8">
          <router-outlet />
        </main>
      </div>
    </div>
    <app-mobile-nav />
    <app-terms-gate />
  `,
})
export class MainLayoutComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly workspaces = inject(WorkspaceService);

  ngOnInit(): void {
    this.auth.ensureProfile().subscribe();
    this.workspaces.loadWorkspaces().subscribe();
  }
}
