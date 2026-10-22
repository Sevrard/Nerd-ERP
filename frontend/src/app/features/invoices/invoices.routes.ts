import { Routes } from '@angular/router';

export const INVOICES_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./invoices-list-page.component').then((m) => m.InvoicesListPageComponent),
  },
  {
    path: ':id',
    loadComponent: () => import('./invoices-detail-page.component').then((m) => m.InvoicesDetailPageComponent),
  },
];
