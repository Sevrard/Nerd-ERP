import { Injectable, inject } from '@angular/core';
import { ApiClient } from '../../core/api/api-client.service';
import {
  InvitationResult,
  InviteUserPayload,
  TenantUser,
  TenantUserUpdatePayload,
} from '../../shared/models/account.model';

/** Utilisateurs du compte courant (`/api/v1/users`, réservé aux administrateurs du compte). */
@Injectable({ providedIn: 'root' })
export class UsersApiService {
  private readonly api = inject(ApiClient);

  list() {
    return this.api.get<TenantUser[]>('/users');
  }

  invite(payload: InviteUserPayload) {
    return this.api.post<InvitationResult>('/users/invitations', payload);
  }

  resendInvitation(userId: string) {
    return this.api.post<InvitationResult>(`/users/${userId}/invitations`, {});
  }

  update(userId: string, payload: TenantUserUpdatePayload) {
    return this.api.patch<TenantUser>(`/users/${userId}`, payload);
  }

  remove(userId: string) {
    return this.api.delete(`/users/${userId}`);
  }
}
