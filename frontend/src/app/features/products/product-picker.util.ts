import { Observable, map } from 'rxjs';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { Product } from '../../shared/models/product.model';
import { SEARCH_SELECT_LIMIT } from '../../shared/ui/search-select.component';
import { formatPriceHt } from './product-catalog.util';
import { ProductsApiService } from './products-api.service';

/** Sources d'un `<app-search-select>` de produits actifs. */
export interface ProductPickerSource {
  search: (q: string) => Observable<Product[]>;
  resolve: (id: string) => Observable<Product>;
  label: (p: Product) => string;
  detail: (p: Product) => string;
}

export function productPickerSource(api: ProductsApiService, workspaces: WorkspaceService): ProductPickerSource {
  return {
    search: (q) =>
      api
        .search(workspaces.requireSelectedId(), {
          q: q || undefined,
          activeOnly: true,
          size: SEARCH_SELECT_LIMIT,
          sort: 'name,asc',
        })
        .pipe(map((page) => page.content)),
    resolve: (id) => api.get(id),
    label: (p) => p.name,
    detail: (p) => `${p.referenceCode} · ${formatPriceHt(p.unitPriceHt)} € HT / ${p.unitLabel}`,
  };
}
