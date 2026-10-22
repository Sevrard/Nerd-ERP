import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client.service';
import {
  Customer,
  CustomerHistory,
  CustomerPayload,
  CustomerStatus,
  FollowUp,
  FollowUpPayload,
} from '../../shared/models/customer.model';
import { PageResponse } from '../../shared/models/page.model';

@Injectable({ providedIn: 'root' })
export class CustomersApiService {
  private readonly api = inject(ApiClient);

  search(
    workspaceId: string,
    params: { q?: string; status?: CustomerStatus; page?: number; size?: number },
  ): Observable<PageResponse<Customer>> {
    return this.api.get<PageResponse<Customer>>('/customers', {
      workspaceId,
      q: params.q,
      status: params.status,
      page: params.page ?? 0,
      size: params.size,
    });
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
}
