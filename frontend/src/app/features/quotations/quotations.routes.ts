import { Routes } from '@angular/router';

export const QUOTATIONS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./quotations-list-page.component').then((m) => m.QuotationsListPageComponent),
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./quotations-editor-page.component').then((m) => m.QuotationsEditorPageComponent),
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./quotations-editor-page.component').then((m) => m.QuotationsEditorPageComponent),
  },
];
