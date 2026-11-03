import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client.service';
import { CatalogPackage, CatalogPackagePayload } from '../../shared/models/catalog-package.model';

@Injectable({ providedIn: 'root' })
export class CatalogPackagesApiService {
  private readonly api = inject(ApiClient);

  list(workspaceId: string): Observable<CatalogPackage[]> {
    return this.api.get<CatalogPackage[]>('/catalog-packages', { workspaceId });
  }

  get(id: string): Observable<CatalogPackage> {
    return this.api.get<CatalogPackage>(`/catalog-packages/${id}`);
  }

  create(payload: CatalogPackagePayload): Observable<CatalogPackage> {
    return this.api.post<CatalogPackage>('/catalog-packages', payload);
  }

  update(id: string, payload: CatalogPackagePayload): Observable<CatalogPackage> {
    return this.api.put<CatalogPackage>(`/catalog-packages/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.api.delete(`/catalog-packages/${id}`);
  }
}
