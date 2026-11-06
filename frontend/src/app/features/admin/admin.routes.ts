import { Routes } from '@angular/router';

export const ADMIN_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'tenants' },
  {
    path: 'tenants',
    loadComponent: () =>
      import('./admin-tenants-page.component').then((m) => m.AdminTenantsPageComponent),
  },
  {
    path: 'tenants/:id',
    loadComponent: () =>
      import('./admin-tenant-detail-page.component').then((m) => m.AdminTenantDetailPageComponent),
  },
];
