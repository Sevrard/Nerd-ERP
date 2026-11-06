import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client.service';
import { PageQuery, PageResponse } from '../../shared/models/page.model';
import { Payment, PaymentPayload } from '../../shared/models/payment.model';

@Injectable({ providedIn: 'root' })
export class PaymentsApiService {
  private readonly api = inject(ApiClient);

  list(workspaceId: string, query: PageQuery = {}): Observable<PageResponse<Payment>> {
    return this.api.get<PageResponse<Payment>>('/payments', { workspaceId, ...query });
  }

  record(payload: PaymentPayload): Observable<Payment> {
    return this.api.post<Payment>('/payments', payload);
  }
}
