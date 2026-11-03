import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client.service';
import { ProductCategory, ProductCategoryPayload } from '../../shared/models/product-category.model';

@Injectable({ providedIn: 'root' })
export class ProductCategoriesApiService {
  private readonly api = inject(ApiClient);

  list(workspaceId: string): Observable<ProductCategory[]> {
    return this.api.get<ProductCategory[]>('/product-categories', { workspaceId });
  }

  get(id: string): Observable<ProductCategory> {
    return this.api.get<ProductCategory>(`/product-categories/${id}`);
  }

  create(payload: ProductCategoryPayload): Observable<ProductCategory> {
    return this.api.post<ProductCategory>('/product-categories', payload);
  }

  update(id: string, payload: ProductCategoryPayload): Observable<ProductCategory> {
    return this.api.put<ProductCategory>(`/product-categories/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.api.delete(`/product-categories/${id}`);
  }
}
