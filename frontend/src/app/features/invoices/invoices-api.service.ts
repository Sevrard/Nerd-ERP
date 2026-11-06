import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client.service';
import { Invoice } from '../../shared/models/invoice.model';
import { PageQuery, PageResponse } from '../../shared/models/page.model';

@Injectable({ providedIn: 'root' })
export class InvoicesApiService {
  private readonly api = inject(ApiClient);

  list(
    workspaceId: string,
    query: { customerId?: string; openOnly?: boolean; q?: string } & PageQuery = {},
  ): Observable<PageResponse<Invoice>> {
    return this.api.get<PageResponse<Invoice>>('/invoices', { workspaceId, ...query });
  }

  get(id: string): Observable<Invoice> {
    return this.api.get<Invoice>(`/invoices/${id}`);
  }

  downloadPdf(id: string): Observable<Blob> {
    return this.api.download(`/documents/invoices/${id}/download`);
  }

  delete(id: string): Observable<void> {
    return this.api.delete(`/invoices/${id}`);
  }
}
