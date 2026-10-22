import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client.service';
import { PageResponse } from '../../shared/models/page.model';
import { MaintenanceContract, MaintenanceContractPayload } from '../../shared/models/maintenance.model';

@Injectable({ providedIn: 'root' })
export class MaintenanceApiService {
  private readonly api = inject(ApiClient);

  list(workspaceId: string): Observable<PageResponse<MaintenanceContract>> {
    return this.api.get<PageResponse<MaintenanceContract>>('/maintenance-contracts', { workspaceId });
  }

  create(payload: MaintenanceContractPayload): Observable<MaintenanceContract> {
    return this.api.post<MaintenanceContract>('/maintenance-contracts', payload);
  }
}
