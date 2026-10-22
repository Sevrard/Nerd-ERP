import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client.service';
import { Invoice } from '../../shared/models/invoice.model';
import { PageResponse } from '../../shared/models/page.model';
import { Quotation, QuotationPayload } from '../../shared/models/quotation.model';

@Injectable({ providedIn: 'root' })
export class QuotationsApiService {
  private readonly api = inject(ApiClient);

  list(workspaceId: string): Observable<PageResponse<Quotation>> {
    return this.api.get<PageResponse<Quotation>>('/quotations', { workspaceId });
  }

  get(id: string): Observable<Quotation> {
    return this.api.get<Quotation>(`/quotations/${id}`);
  }

  create(payload: QuotationPayload): Observable<Quotation> {
    return this.api.post<Quotation>('/quotations', payload);
  }

  update(id: string, payload: QuotationPayload): Observable<Quotation> {
    return this.api.put<Quotation>(`/quotations/${id}`, payload);
  }

  send(id: string): Observable<Quotation> {
    return this.api.post<Quotation>(`/quotations/${id}/send`, {});
  }

  accept(id: string): Observable<Quotation> {
    return this.api.post<Quotation>(`/quotations/${id}/accept`, {});
  }

  convertToInvoice(id: string): Observable<Invoice> {
    return this.api.post<Invoice>(`/quotations/${id}/convert-to-invoice`, {});
  }
}
