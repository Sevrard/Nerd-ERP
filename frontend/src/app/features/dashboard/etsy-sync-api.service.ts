import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client.service';
import { EtsySyncCommitRequest, EtsySyncCommitResponse, EtsySyncFetchResponse } from '../../shared/models/etsy-sync.model';

@Injectable({ providedIn: 'root' })
export class EtsySyncApiService {
  private readonly api = inject(ApiClient);

  fetch(workspaceId: string): Observable<EtsySyncFetchResponse> {
    return this.api.post<EtsySyncFetchResponse>('/etsy-sync/fetch', {}, { workspaceId });
  }

  commit(workspaceId: string, body: EtsySyncCommitRequest): Observable<EtsySyncCommitResponse> {
    return this.api.post<EtsySyncCommitResponse>('/etsy-sync/commit', body, { workspaceId });
  }
}
