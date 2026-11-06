import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, map, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { MODULES } from '../../core/modules/module.service';
import { AdminModuleUpdate, AdminTenantDetail } from '../../shared/models/admin.model';
import { ModuleCode, TenantRole } from '../../shared/models/auth.model';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { apiErrorMessage } from '../../shared/utils/api-error.util';
import { AdminApiService } from './admin-api.service';

interface ModuleDraft {
  code: ModuleCode;
  enabled: boolean;
  expiresAt: string;
}

const ROLE_LABELS: Record<TenantRole, string> = {
  OWNER: 'Propriétaire',
  ADMIN: 'Administrateur',
  USER: 'Utilisateur',
  READONLY: 'Lecture seule',
};

@Component({
  selector: 'app-admin-tenant-detail-page',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink, LoadingStateComponent, ErrorBannerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-6">
      <a routerLink="/admin/tenants" class="text-sm text-slate-400 hover:text-white">← Comptes clients</a>

      @if (error()) {
        <app-error-banner [message]="error()!" />
      }
      @if (loading() && !tenant()) {
        <app-loading-state />
      } @else if (tenant(); as t) {
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p class="text-xs font-semibold uppercase tracking-wider text-orange-400">Compte client</p>
            <h1 class="text-2xl font-bold text-white">{{ t.name }}</h1>
            <p class="mt-1 font-mono text-xs text-slate-500">{{ t.slug }} · créé le {{ t.createdAt | date: 'dd/MM/yyyy' }}</p>
          </div>
          <div class="flex items-center gap-2">
            @if (t.active) {
              <span class="rounded-full border border-emerald-500/30 bg-emerald-950/40 px-2 py-0.5 text-xs text-emerald-300">Actif</span>
              @if (!isOwnTenant()) {
                <button
                  type="button"
                  class="rounded-lg border border-red-500/40 px-3 py-1.5 text-sm text-red-300 hover:bg-red-950/40 disabled:opacity-50"
                  [disabled]="busy()"
                  (click)="setActive(false)"
                >
                  Suspendre
                </button>
              }
            } @else {
              <span class="rounded-full border border-red-500/30 bg-red-950/40 px-2 py-0.5 text-xs text-red-300">Suspendu</span>
              <button
                type="button"
                class="rounded-lg bg-emerald-700 px-3 py-1.5 text-sm text-white hover:bg-emerald-600 disabled:opacity-50"
                [disabled]="busy()"
                (click)="setActive(true)"
              >
                Réactiver
              </button>
            }
          </div>
        </div>

        @if (actionError()) {
          <app-error-banner [message]="actionError()!" />
        }

        <div class="grid gap-6 lg:grid-cols-2">
          <section class="rounded-xl border border-slate-800 bg-slate-900/50 p-6">
            <h2 class="text-lg font-semibold text-white">Informations</h2>
            <form class="mt-4 flex flex-wrap items-end gap-3" [formGroup]="nameForm" (ngSubmit)="saveName()">
              <label class="block flex-1 text-sm text-slate-300">
                Nom du compte
                <input formControlName="name" required maxlength="255" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" />
              </label>
              <button type="submit" class="rounded-lg bg-slate-800 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50" [disabled]="busy() || nameForm.invalid || nameForm.pristine">
                Renommer
              </button>
            </form>
            <dl class="mt-4 grid grid-cols-2 gap-3 text-sm">
              <dt class="text-slate-500">Workspaces actifs</dt>
              <dd class="text-white">{{ t.workspaceCount }}</dd>
              <dt class="text-slate-500">Utilisateurs</dt>
              <dd class="text-white">{{ t.users.length }}</dd>
              <dt class="text-slate-500">Dernière modification</dt>
              <dd class="text-white">{{ t.updatedAt | date: 'dd/MM/yyyy HH:mm' }}</dd>
            </dl>
          </section>

          <section class="rounded-xl border border-slate-800 bg-slate-900/50 p-6">
            <h2 class="text-lg font-semibold text-white">Modules</h2>
            <p class="mt-1 text-xs text-slate-500">Le socle (clients, produits, devis, factures, paiements) est toujours inclus.</p>
            <div class="mt-4 space-y-3">
              @for (m of moduleDrafts(); track m.code) {
                <div class="rounded-lg border border-slate-800 bg-slate-950/40 p-3">
                  <label class="flex cursor-pointer items-start gap-3 text-sm">
                    <input type="checkbox" class="mt-0.5" [checked]="m.enabled" (change)="toggleModule(m.code)" />
                    <span class="flex-1">
                      <span class="block font-medium text-white">{{ info(m.code).label }}</span>
                      <span class="block text-xs text-slate-500">{{ info(m.code).description }}</span>
                    </span>
                    @if (state(m.code); as st) {
                      @if (st.currentlyActive) {
                        <span class="rounded-full border border-emerald-500/30 px-2 py-0.5 text-[11px] text-emerald-300">actif</span>
                      } @else if (st.enabled) {
                        <span class="rounded-full border border-amber-500/30 px-2 py-0.5 text-[11px] text-amber-300">expiré</span>
                      }
                    }
                  </label>
                  @if (m.enabled) {
                    <label class="mt-2 flex items-center gap-2 text-xs text-slate-400">
                      Fin de validité (optionnel)
                      <input type="date" class="rounded-md border border-slate-700 bg-slate-950 px-2 py-1 text-xs text-white" [value]="m.expiresAt" (change)="setExpiry(m.code, $any($event.target).value)" />
                    </label>
                  }
                </div>
              }
            </div>
            <div class="mt-4 flex items-center justify-end gap-3">
              @if (modulesSaved()) {
                <span class="text-xs text-emerald-300">Modules enregistrés</span>
              }
              <button type="button" class="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500 disabled:opacity-50" [disabled]="busy() || !modulesDirty()" (click)="saveModules()">
                Enregistrer les modules
              </button>
            </div>
          </section>
        </div>

        <section class="rounded-xl border border-slate-800 bg-slate-900/50 p-6">
          <h2 class="text-lg font-semibold text-white">Utilisateurs</h2>
          <div class="mt-4 overflow-hidden rounded-xl border border-slate-800">
            <table class="min-w-full divide-y divide-slate-800 text-sm">
              <thead class="bg-slate-900/80 text-left text-xs uppercase text-slate-500">
                <tr>
                  <th class="px-4 py-3">E-mail</th>
                  <th class="px-4 py-3">Nom</th>
                  <th class="px-4 py-3">Rôle</th>
                  <th class="px-4 py-3">Statut</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-800">
                @for (u of t.users; track u.userId) {
                  <tr>
                    <td class="px-4 py-3 text-white">{{ u.email }}</td>
                    <td class="px-4 py-3 text-slate-300">{{ u.displayName }}</td>
                    <td class="px-4 py-3 text-slate-300">
                      {{ roleLabel(u.role) }}
                      @if (u.platformAdmin) {
                        <span class="ml-1 rounded-md border border-orange-500/30 px-1.5 py-0.5 text-[11px] text-orange-300">éditeur</span>
                      }
                    </td>
                    <td class="px-4 py-3 text-slate-400">{{ u.enabled ? 'Actif' : 'Désactivé' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <form class="mt-4 grid gap-3 sm:grid-cols-4" [formGroup]="userForm" (ngSubmit)="addUser()">
            <label class="block text-sm text-slate-300">
              E-mail
              <input type="email" formControlName="email" required class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" />
            </label>
            <label class="block text-sm text-slate-300">
              Nom affiché
              <input formControlName="displayName" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" />
            </label>
            <label class="block text-sm text-slate-300">
              Mot de passe <span class="text-slate-500">(si nouveau)</span>
              <input type="password" formControlName="password" autocomplete="new-password" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" />
            </label>
            <label class="block text-sm text-slate-300">
              Rôle
              <select formControlName="role" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white">
                @for (r of roles; track r) {
                  <option [value]="r">{{ roleLabel(r) }}</option>
                }
              </select>
            </label>
            @if (userError()) {
              <p class="text-sm text-red-400 sm:col-span-4">{{ userError() }}</p>
            }
            <button type="submit" class="rounded-lg bg-slate-800 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50 sm:w-fit" [disabled]="busy() || userForm.invalid">
              Ajouter l’utilisateur
            </button>
          </form>
        </section>
      }
    </div>
  `,
})
export class AdminTenantDetailPageComponent {
  private readonly api = inject(AdminApiService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  readonly roles: TenantRole[] = ['OWNER', 'ADMIN', 'USER', 'READONLY'];
  readonly tenant = signal<AdminTenantDetail | null>(null);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly userError = signal<string | null>(null);
  readonly modulesSaved = signal(false);
  readonly moduleDrafts = signal<ModuleDraft[]>([]);

  readonly isOwnTenant = computed(() => this.tenant()?.id === this.auth.profile()?.tenantId);
  readonly modulesDirty = computed(() => {
    const t = this.tenant();
    if (!t) {
      return false;
    }
    return this.moduleDrafts().some((d) => {
      const st = t.modules.find((m) => m.code === d.code);
      return !st || st.enabled !== d.enabled || (st.expiresAt ?? '') !== d.expiresAt;
    });
  });

  readonly nameForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
  });
  readonly userForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    displayName: [''],
    password: ['', [Validators.minLength(8), Validators.maxLength(128)]],
    role: ['USER' as TenantRole, Validators.required],
  });

  constructor() {
    this.route.paramMap
      .pipe(
        map((p) => p.get('id') ?? ''),
        switchMap((id) => {
          this.loading.set(true);
          this.error.set(null);
          return this.api.getTenant(id).pipe(finalize(() => this.loading.set(false)));
        }),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (t) => this.apply(t),
        error: (err) => this.error.set(apiErrorMessage(err, 'Compte introuvable')),
      });
  }

  info(code: ModuleCode) {
    return MODULES.find((m) => m.code === code) ?? { code, label: code, description: '' };
  }

  state(code: ModuleCode) {
    return this.tenant()?.modules.find((m) => m.code === code) ?? null;
  }

  roleLabel(role: TenantRole): string {
    return ROLE_LABELS[role] ?? role;
  }

  toggleModule(code: ModuleCode): void {
    this.modulesSaved.set(false);
    this.moduleDrafts.update((list) => list.map((d) => (d.code === code ? { ...d, enabled: !d.enabled } : d)));
  }

  setExpiry(code: ModuleCode, value: string): void {
    this.modulesSaved.set(false);
    this.moduleDrafts.update((list) => list.map((d) => (d.code === code ? { ...d, expiresAt: value } : d)));
  }

  saveModules(): void {
    const t = this.tenant();
    if (!t || this.busy()) {
      return;
    }
    const updates: AdminModuleUpdate[] = this.moduleDrafts().map((d) => ({
      code: d.code,
      enabled: d.enabled,
      expiresAt: d.enabled && d.expiresAt ? d.expiresAt : null,
    }));
    this.run(this.api.updateModules(t.id, updates), () => this.modulesSaved.set(true));
  }

  setActive(active: boolean): void {
    const t = this.tenant();
    if (!t || this.busy()) {
      return;
    }
    if (!active && !confirm(`Suspendre le compte « ${t.name} » ?\n\nSes utilisateurs perdront immédiatement l’accès à l’application.`)) {
      return;
    }
    this.run(this.api.updateTenant(t.id, { active }));
  }

  saveName(): void {
    const t = this.tenant();
    if (!t || this.busy() || this.nameForm.invalid) {
      return;
    }
    this.run(this.api.updateTenant(t.id, { name: this.nameForm.getRawValue().name.trim() }));
  }

  addUser(): void {
    const t = this.tenant();
    if (!t || this.busy() || this.userForm.invalid) {
      return;
    }
    const v = this.userForm.getRawValue();
    this.userError.set(null);
    this.busy.set(true);
    this.api
      .addUser(t.id, {
        email: v.email.trim(),
        displayName: v.displayName.trim() || null,
        password: v.password || null,
        role: v.role,
      })
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (updated) => {
          this.apply(updated);
          this.userForm.reset({ email: '', displayName: '', password: '', role: 'USER' });
        },
        error: (err) => this.userError.set(apiErrorMessage(err, 'Ajout impossible')),
      });
  }

  private run(request: ReturnType<AdminApiService['updateTenant']>, onDone?: () => void): void {
    this.busy.set(true);
    this.actionError.set(null);
    request.pipe(finalize(() => this.busy.set(false))).subscribe({
      next: (updated) => {
        this.apply(updated);
        onDone?.();
      },
      error: (err) => this.actionError.set(apiErrorMessage(err, 'Opération impossible')),
    });
  }

  private apply(t: AdminTenantDetail): void {
    this.tenant.set(t);
    this.nameForm.reset({ name: t.name });
    this.moduleDrafts.set(
      MODULES.map((m) => {
        const st = t.modules.find((x) => x.code === m.code);
        return { code: m.code, enabled: st?.enabled ?? false, expiresAt: st?.expiresAt ?? '' };
      }),
    );
  }
}
