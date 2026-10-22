import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { ApiClient } from '../api/api-client.service';
import { LoginRequest, LoginResponse, MeResponse, TenantRole } from '../../shared/models/auth.model';

const TOKEN_KEY = 'nerd-erp.token';
const TENANT_SLUG_KEY = 'nerd-erp.tenant-slug';
const ROLE_KEY = 'nerd-erp.role';

function isAdminRole(role: string | null | undefined): boolean {
  const r = String(role ?? '').toUpperCase();
  return r === 'OWNER' || r === 'ADMIN';
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiClient);
  private readonly router = inject(Router);

  private readonly tokenSignal = signal<string | null>(this.readToken());
  private readonly profileSignal = signal<MeResponse | null>(null);

  readonly token = this.tokenSignal.asReadonly();
  readonly profile = this.profileSignal.asReadonly();
  readonly isAuthenticated = computed(() => !!this.tokenSignal());
  readonly canAdminister = computed(() => {
    const role = this.profileSignal()?.role ?? this.readStoredRole();
    return isAdminRole(role);
  });

  login(payload: LoginRequest) {
    return this.api.post<LoginResponse>('/auth/login', payload).pipe(
      tap((res) => {
        localStorage.setItem(TOKEN_KEY, res.accessToken);
        localStorage.setItem(TENANT_SLUG_KEY, payload.tenantSlug);
        localStorage.setItem(ROLE_KEY, res.role);
        this.tokenSignal.set(res.accessToken);
        this.profileSignal.set({
          userId: res.userId,
          tenantId: res.tenantId,
          email: res.email,
          role: res.role,
          workspaces: [],
        });
      }),
    );
  }

  loadProfile() {
    return this.api.get<MeResponse>('/auth/me').pipe(
      tap((me) => {
        localStorage.setItem(ROLE_KEY, me.role);
        this.profileSignal.set(me);
      }),
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(ROLE_KEY);
    this.tokenSignal.set(null);
    this.profileSignal.set(null);
    void this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return this.tokenSignal();
  }

  getRole(): TenantRole | null {
    return this.profileSignal()?.role ?? null;
  }

  getStoredTenantSlug(): string {
    return localStorage.getItem(TENANT_SLUG_KEY) ?? 'nerd-group';
  }

  private readToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  private readStoredRole(): TenantRole | null {
    const role = localStorage.getItem(ROLE_KEY);
    return role ? (role as TenantRole) : null;
  }
}
