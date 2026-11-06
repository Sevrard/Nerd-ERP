import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client.service';
import { CsvImportResult } from '../../shared/models/onboarding.model';
import { PageQuery, PageResponse } from '../../shared/models/page.model';
import { Product, ProductPayload, ProductStats } from '../../shared/models/product.model';

@Injectable({ providedIn: 'root' })
export class ProductsApiService {
  private readonly api = inject(ApiClient);

  search(
    workspaceId: string,
    params?: { q?: string; activeOnly?: boolean; categoryId?: string } & PageQuery,
  ): Observable<PageResponse<Product>> {
    return this.api.get<PageResponse<Product>>('/products', {
      workspaceId,
      q: params?.q,
      activeOnly: params?.activeOnly ?? true,
      categoryId: params?.categoryId,
      page: params?.page ?? 0,
      size: params?.size,
      sort: params?.sort,
    });
  }

  stats(workspaceId: string): Observable<ProductStats> {
    return this.api.get<ProductStats>('/products/stats', { workspaceId });
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

  importCsv(workspaceId: string, file: File, dryRun: boolean): Observable<CsvImportResult> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.api.upload<CsvImportResult>('/products/import', form, { workspaceId, dryRun });
  }

  importTemplate(): Observable<Blob> {
    return this.api.download('/products/import/template');
  }
}
