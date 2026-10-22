export type TenantRole = 'OWNER' | 'ADMIN' | 'USER' | 'READONLY';

export interface LoginRequest {
  email: string;
  password: string;
  tenantSlug: string;
}

export interface LoginResponse {
  accessToken: string;
  userId: string;
  tenantId: string;
  role: TenantRole;
  email: string;
}

export interface MeResponse {
  userId: string;
  tenantId: string;
  email: string;
  role: TenantRole;
  workspaces: import('./workspace.model').Workspace[];
}
