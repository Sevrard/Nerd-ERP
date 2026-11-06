import { Observable, map } from 'rxjs';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { Customer, customerDisplayName } from '../../shared/models/customer.model';
import { SEARCH_SELECT_LIMIT } from '../../shared/ui/search-select.component';
import { CustomersApiService } from './customers-api.service';

/** Sources d'un `<app-search-select>` de clients (non archivés, tri alphabétique). */
export interface CustomerPickerSource {
  search: (q: string) => Observable<Customer[]>;
  resolve: (id: string) => Observable<Customer>;
  label: (c: Customer) => string;
  detail: (c: Customer) => string | null;
}

export function customerPickerSource(api: CustomersApiService, workspaces: WorkspaceService): CustomerPickerSource {
  return {
    search: (q) =>
      api
        .search(workspaces.requireSelectedId(), {
          q: q || undefined,
          size: SEARCH_SELECT_LIMIT,
          sort: 'companyName,lastName,firstName,asc',
        })
        .pipe(map((page) => page.content.filter((c) => !c.archived))),
    resolve: (id) => api.get(id),
    label: (c) => customerDisplayName(c),
    detail: (c) => c.email ?? ([c.postalCode, c.city].filter(Boolean).join(' ') || null),
  };
}
