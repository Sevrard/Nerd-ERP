import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client.service';
import {
  Customer,
  CustomerHistory,
  CustomerPayload,
  CustomerStats,
  CustomerStatus,
  FollowUp,
  FollowUpPayload,
} from '../../shared/models/customer.model';
import { CsvImportResult } from '../../shared/models/onboarding.model';
import { PageQuery, PageResponse } from '../../shared/models/page.model';

@Injectable({ providedIn: 'root' })
export class CustomersApiService {
  private readonly api = inject(ApiClient);

  search(
    workspaceId: string,
    params: { q?: string; status?: CustomerStatus } & PageQuery,
  ): Observable<PageResponse<Customer>> {
    return this.api.get<PageResponse<Customer>>('/customers', {
      workspaceId,
      q: params.q,
      status: params.status,
      page: params.page ?? 0,
      size: params.size,
      sort: params.sort,
    });
  }

  stats(workspaceId: string): Observable<CustomerStats> {
    return this.api.get<CustomerStats>('/customers/stats', { workspaceId });
  }

  get(id: string): Observable<Customer> {
    return this.api.get<Customer>(`/customers/${id}`);
  }

  create(payload: CustomerPayload): Observable<Customer> {
    return this.api.post<Customer>('/customers', payload);
  }

  update(id: string, payload: CustomerPayload): Observable<Customer> {
    return this.api.put<Customer>(`/customers/${id}`, payload);
  }

  archive(id: string): Observable<Customer> {
    return this.api.post<Customer>(`/customers/${id}/archive`, {});
  }

  delete(id: string): Observable<void> {
    return this.api.delete(`/customers/${id}`);
  }

  history(id: string): Observable<CustomerHistory> {
    return this.api.get<CustomerHistory>(`/customers/${id}/history`);
  }

  scheduleFollowUp(id: string, payload: FollowUpPayload): Observable<FollowUp> {
    return this.api.post<FollowUp>(`/customers/${id}/follow-ups`, payload);
  }

  importCsv(workspaceId: string, file: File, dryRun: boolean): Observable<CsvImportResult> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.api.upload<CsvImportResult>('/customers/import', form, { workspaceId, dryRun });
  }

  importTemplate(): Observable<Blob> {
    return this.api.download('/customers/import/template');
  }
}
