import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client.service';
import {
  CreditNotePayload,
  DocumentEmailPayload,
  DocumentEmailResult,
  Invoice,
} from '../../shared/models/invoice.model';
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

  /** Export comptable CSV des documents émis sur la période (dates ISO yyyy-MM-dd). */
  exportCsv(workspaceId: string, from: string, to: string): Observable<Blob> {
    return this.api.download(`/invoices/export`, { workspaceId, from, to });
  }

  delete(id: string): Observable<void> {
    return this.api.delete(`/invoices/${id}`);
  }

  createCreditNote(id: string, payload: CreditNotePayload): Observable<Invoice> {
    return this.api.post<Invoice>(`/invoices/${id}/credit-notes`, payload);
  }

  sendByEmail(id: string, payload: DocumentEmailPayload): Observable<DocumentEmailResult> {
    return this.api.post<DocumentEmailResult>(`/invoices/${id}/email`, payload);
  }

  sendReminder(id: string, payload: DocumentEmailPayload): Observable<DocumentEmailResult> {
    return this.api.post<DocumentEmailResult>(`/invoices/${id}/reminders`, payload);
  }
}
