import { Injectable, inject } from '@angular/core';
import { ApiClient } from '../../core/api/api-client.service';
import {
  AdminModuleDefinition,
  AdminModuleUpdate,
  AdminTenantCreatePayload,
  AdminTenantDetail,
  AdminTenantSummary,
  AdminTenantUpdatePayload,
  AdminTenantUserCreatePayload,
} from '../../shared/models/admin.model';
import { PageQuery, PageResponse } from '../../shared/models/page.model';

/** API d'administration plateforme (`/api/v1/admin/**`, réservée aux administrateurs éditeur). */
@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly api = inject(ApiClient);

  listModules() {
    return this.api.get<AdminModuleDefinition[]>('/admin/modules');
  }

  listTenants(query: { q?: string } & PageQuery) {
    return this.api.get<PageResponse<AdminTenantSummary>>('/admin/tenants', { ...query });
  }

  getTenant(id: string) {
    return this.api.get<AdminTenantDetail>(`/admin/tenants/${id}`);
  }

  createTenant(payload: AdminTenantCreatePayload) {
    return this.api.post<AdminTenantDetail>('/admin/tenants', payload);
  }

  updateTenant(id: string, payload: AdminTenantUpdatePayload) {
    return this.api.patch<AdminTenantDetail>(`/admin/tenants/${id}`, payload);
  }

  updateModules(id: string, modules: AdminModuleUpdate[]) {
    return this.api.put<AdminTenantDetail>(`/admin/tenants/${id}/modules`, { modules });
  }

  addUser(id: string, payload: AdminTenantUserCreatePayload) {
    return this.api.post<AdminTenantDetail>(`/admin/tenants/${id}/users`, payload);
  }

  resendInvitation(id: string, userId: string) {
    return this.api.post<AdminTenantDetail>(`/admin/tenants/${id}/users/${userId}/invitations`, {});
  }
}
