import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client.service';
import { Merchant } from '../../shared/models/merchant.model';

@Injectable({ providedIn: 'root' })
export class MerchantsApiService {
  private readonly api = inject(ApiClient);

  list(workspaceId: string): Observable<Merchant[]> {
    return this.api.get<Merchant[]>('/merchants', { workspaceId });
  }
}
