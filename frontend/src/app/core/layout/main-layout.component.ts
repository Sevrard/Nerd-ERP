import { Component, inject, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { WorkspaceService } from '../workspace/workspace.service';
import { SidebarComponent } from './sidebar.component';
import { HeaderComponent } from './header.component';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, HeaderComponent],
  template: `
    <div class="flex min-h-screen h-screen max-h-screen overflow-hidden">
      <app-sidebar />
      <div class="flex min-h-0 min-w-0 flex-1 flex-col">
        <app-header />
        <main class="flex-1 overflow-y-auto p-4 sm:p-6 lg:p-8">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class MainLayoutComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly workspaces = inject(WorkspaceService);

  ngOnInit(): void {
    this.auth.loadProfile().subscribe();
    this.workspaces.loadWorkspaces().subscribe();
  }
}
