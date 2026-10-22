import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client.service';
import { PageResponse } from '../../shared/models/page.model';
import { Product, ProductPayload } from '../../shared/models/product.model';

@Injectable({ providedIn: 'root' })
export class ProductsApiService {
  private readonly api = inject(ApiClient);

  search(workspaceId: string, params?: { q?: string; activeOnly?: boolean }): Observable<PageResponse<Product>> {
    return this.api.get<PageResponse<Product>>('/products', {
      workspaceId,
      q: params?.q,
      activeOnly: params?.activeOnly ?? true,
    });
  }

  get(id: string): Observable<Product> {
    return this.api.get<Product>(`/products/${id}`);
  }

  create(payload: ProductPayload): Observable<Product> {
    return this.api.post<Product>('/products', payload);
  }

  update(id: string, payload: ProductPayload): Observable<Product> {
    return this.api.put<Product>(`/products/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.api.delete(`/products/${id}`);
  }
}
