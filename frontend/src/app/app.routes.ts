import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { MainLayoutComponent } from './core/layout/main-layout.component';
import { moduleGuard, platformAdminGuard } from './core/modules/module.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login-page.component').then((m) => m.LoginPageComponent),
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./features/auth/forgot-password-page.component').then((m) => m.ForgotPasswordPageComponent),
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./features/auth/reset-password-page.component').then((m) => m.ResetPasswordPageComponent),
  },
  {
    path: 'activate',
    loadComponent: () =>
      import('./features/auth/activate-page.component').then((m) => m.ActivatePageComponent),
  },
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard-page.component').then((m) => m.DashboardPageComponent),
      },
      {
        path: 'customers',
        loadChildren: () =>
          import('./features/customers/customers.routes').then((m) => m.CUSTOMERS_ROUTES),
      },
      {
        path: 'products',
        loadChildren: () =>
          import('./features/products/products.routes').then((m) => m.PRODUCTS_ROUTES),
      },
      {
        path: 'quotations',
        loadChildren: () =>
          import('./features/quotations/quotations.routes').then((m) => m.QUOTATIONS_ROUTES),
      },
      {
        path: 'invoices',
        loadChildren: () =>
          import('./features/invoices/invoices.routes').then((m) => m.INVOICES_ROUTES),
      },
      {
        path: 'payments',
        loadComponent: () =>
          import('./features/payments/payments-page.component').then((m) => m.PaymentsPageComponent),
      },
      {
        path: 'maintenance',
        canActivate: [moduleGuard('MAINTENANCE')],
        loadComponent: () =>
          import('./features/maintenance/maintenance-page.component').then((m) => m.MaintenancePageComponent),
      },
      {
        path: 'settings/mail',
        loadComponent: () =>
          import('./features/settings/workspace-mail-settings-page.component').then(
            (m) => m.WorkspaceMailSettingsPageComponent,
          ),
      },
      {
        path: 'settings/users',
        loadComponent: () =>
          import('./features/settings/users-page.component').then((m) => m.UsersPageComponent),
      },
      {
        path: 'settings/profile',
        loadComponent: () =>
          import('./features/settings/profile-page.component').then((m) => m.ProfilePageComponent),
      },
      {
        path: 'module-disabled',
        loadComponent: () =>
          import('./core/modules/module-disabled-page.component').then((m) => m.ModuleDisabledPageComponent),
      },
      {
        path: 'admin',
        canActivate: [platformAdminGuard],
        loadChildren: () => import('./features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
