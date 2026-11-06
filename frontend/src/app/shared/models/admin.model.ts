import { ModuleCode, TenantRole } from './auth.model';

export interface AdminModuleDefinition {
  code: ModuleCode;
  label: string;
  description: string;
}

export interface AdminTenantModuleState {
  code: ModuleCode;
  label: string;
  enabled: boolean;
  currentlyActive: boolean;
  activatedAt: string | null;
  expiresAt: string | null;
}

export interface AdminTenantSummary {
  id: string;
  name: string;
  slug: string;
  active: boolean;
  createdAt: string;
  userCount: number;
  activeModules: ModuleCode[];
}

export interface AdminTenantUser {
  userId: string;
  email: string;
  displayName: string;
  role: TenantRole;
  enabled: boolean;
  platformAdmin: boolean;
}

export interface AdminTenantDetail {
  id: string;
  name: string;
  slug: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  workspaceCount: number;
  users: AdminTenantUser[];
  modules: AdminTenantModuleState[];
}

export interface AdminTenantCreatePayload {
  name: string;
  slug: string | null;
  legalName: string | null;
  ownerEmail: string;
  ownerDisplayName: string | null;
  ownerPassword: string | null;
  workspaceName: string | null;
  modules: ModuleCode[];
}

export interface AdminTenantUpdatePayload {
  name?: string;
  active?: boolean;
}

export interface AdminModuleUpdate {
  code: ModuleCode;
  enabled: boolean;
  expiresAt: string | null;
}

export interface AdminTenantUserCreatePayload {
  email: string;
  displayName: string | null;
  password: string | null;
  role: TenantRole;
}
