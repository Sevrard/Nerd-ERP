import { Injectable, inject } from '@angular/core';
import { ApiClient } from '../../core/api/api-client.service';
import { ActivatePayload, Profile, TokenInfo } from '../../shared/models/account.model';
import { LoginResponse } from '../../shared/models/auth.model';
import { LoginBranding, OnboardingChecklist, TermsStatus } from '../../shared/models/onboarding.model';

/** Parcours de compte : routes publiques (`/auth/public/**`) et profil de l'utilisateur connecté. */
@Injectable({ providedIn: 'root' })
export class AccountApiService {
  private readonly api = inject(ApiClient);

  forgotPassword(email: string) {
    return this.api.post<void>('/auth/public/forgot-password', { email });
  }

  tokenInfo(token: string) {
    return this.api.get<TokenInfo>('/auth/public/token-info', { token });
  }

  resetPassword(token: string, password: string) {
    return this.api.post<void>('/auth/public/reset-password', { token, password });
  }

  activate(payload: ActivatePayload) {
    return this.api.post<LoginResponse>('/auth/public/activate', payload);
  }

  getProfile() {
    return this.api.get<Profile>('/account/profile');
  }

  updateProfile(displayName: string) {
    return this.api.put<Profile>('/account/profile', { displayName });
  }

  changePassword(currentPassword: string, newPassword: string) {
    return this.api.post<void>('/account/password', { currentPassword, newPassword });
  }

  /** Couleurs / logo du compte client pour l'écran de connexion (réponse neutre si inconnu). */
  loginBranding(tenantSlug: string) {
    return this.api.get<LoginBranding>('/auth/public/branding', { tenant: tenantSlug });
  }

  termsStatus() {
    return this.api.get<TermsStatus>('/account/terms');
  }

  acceptTerms() {
    return this.api.post<TermsStatus>('/account/terms/accept', {});
  }

  onboardingChecklist(workspaceId: string) {
    return this.api.get<OnboardingChecklist>('/onboarding/checklist', { workspaceId });
  }
}
