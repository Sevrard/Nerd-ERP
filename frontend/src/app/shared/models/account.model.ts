import { TenantRole } from './auth.model';

export type AccountTokenKind = 'INVITATION' | 'PASSWORD_RESET';

/** Ce que l'API révèle d'un lien d'activation / réinitialisation avant toute saisie. */
export interface TokenInfo {
  kind: AccountTokenKind;
  email: string;
  displayName: string;
  tenantName: string | null;
  tenantSlug: string | null;
}

export interface ActivatePayload {
  token: string;
  displayName: string | null;
  password: string;
}

export interface Profile {
  userId: string;
  email: string;
  displayName: string;
  lastLoginAt: string | null;
}

export interface TenantUser {
  userId: string;
  email: string;
  displayName: string;
  role: TenantRole;
  enabled: boolean;
  /** Invitation en attente : mot de passe pas encore choisi. */
  pending: boolean;
  lastLoginAt: string | null;
  memberSince: string;
  self: boolean;
}

export interface InviteUserPayload {
  email: string;
  displayName: string | null;
  role: TenantRole;
}

/** `activationUrl` n'est fourni que si l'e-mail n'a pas pu partir (boîte éditeur non configurée). */
export interface InvitationResult {
  user: TenantUser;
  emailSent: boolean;
  activationUrl: string | null;
}

export interface TenantUserUpdatePayload {
  role?: TenantRole;
  enabled?: boolean;
}
