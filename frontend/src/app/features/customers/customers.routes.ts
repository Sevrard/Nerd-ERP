import { Routes } from '@angular/router';

export const CUSTOMERS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./customers-list-page.component').then((m) => m.CustomersListPageComponent),
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./customers-form-page.component').then((m) => m.CustomersFormPageComponent),
  },
  {
    path: ':id/edit',
    loadComponent: () =>
      import('./customers-form-page.component').then((m) => m.CustomersFormPageComponent),
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./customers-detail-page.component').then((m) => m.CustomersDetailPageComponent),
  },
];
