export type TenantRole = 'OWNER' | 'ADMIN' | 'USER' | 'READONLY';

/** Modules optionnels activables par tenant (miroir de `ModuleCode` côté backend). */
export type ModuleCode = 'MAINTENANCE' | 'INVOICE_SCAN';

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
  platformAdmin: boolean;
}

export interface MeResponse {
  userId: string;
  tenantId: string;
  email: string;
  role: TenantRole;
  platformAdmin: boolean;
  modules: ModuleCode[];
  workspaces: import('./workspace.model').Workspace[];
}
