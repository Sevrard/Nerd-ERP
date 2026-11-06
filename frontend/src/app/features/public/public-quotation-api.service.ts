import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client.service';
import { PublicQuotation } from '../../shared/models/quotation.model';

/** Parcours client sans session : consultation et décision sur un devis via le lien signé. */
@Injectable({ providedIn: 'root' })
export class PublicQuotationApiService {
  private readonly api = inject(ApiClient);

  view(token: string): Observable<PublicQuotation> {
    return this.api.get<PublicQuotation>(`/auth/public/quotations/${encodeURIComponent(token)}`);
  }

  pdf(token: string): Observable<Blob> {
    return this.api.download(`/auth/public/quotations/${encodeURIComponent(token)}/pdf`);
  }

  accept(token: string, name: string, comment: string | null): Observable<PublicQuotation> {
    return this.api.post<PublicQuotation>(`/auth/public/quotations/${encodeURIComponent(token)}/accept`, {
      name,
      comment,
    });
  }

  reject(token: string, name: string, reason: string | null): Observable<PublicQuotation> {
    return this.api.post<PublicQuotation>(`/auth/public/quotations/${encodeURIComponent(token)}/reject`, {
      name,
      reason,
    });
  }
}
