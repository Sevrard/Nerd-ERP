import { Routes } from '@angular/router';

export const PRODUCTS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./products-list-page.component').then((m) => m.ProductsListPageComponent),
  },
  {
    path: 'new',
    loadComponent: () => import('./products-form-page.component').then((m) => m.ProductsFormPageComponent),
  },
  {
    path: ':id/edit',
    loadComponent: () => import('./products-form-page.component').then((m) => m.ProductsFormPageComponent),
  },
];
