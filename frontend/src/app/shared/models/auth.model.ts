export type TenantRole = 'OWNER' | 'ADMIN' | 'USER' | 'READONLY';

/** Libellés métier des rôles (le propriétaire du compte est présenté comme « Admin »). */
export const ROLE_LABELS: Record<TenantRole, string> = {
  OWNER: 'Admin',
  ADMIN: 'Gestionnaire',
  USER: 'Utilisateur',
  READONLY: 'Lecture seule',
};

export function roleLabel(role: TenantRole | null | undefined): string {
  return role ? (ROLE_LABELS[role] ?? role) : '';
}

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
  tenantSlug: string;
  role: TenantRole;
  email: string;
  displayName: string;
  platformAdmin: boolean;
}

export interface MeResponse {
  userId: string;
  tenantId: string;
  email: string;
  displayName: string;
  role: TenantRole;
  platformAdmin: boolean;
  modules: ModuleCode[];
  workspaces: import('./workspace.model').Workspace[];
  /** Absent juste après le login (profil minimal) ; renseigné par `/auth/me`. */
  terms?: import('./onboarding.model').TermsStatus;
}
