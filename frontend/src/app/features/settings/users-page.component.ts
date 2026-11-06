import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { InvitationResult, TenantUser } from '../../shared/models/account.model';
import { TenantRole, roleLabel } from '../../shared/models/auth.model';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { apiErrorMessage } from '../../shared/utils/api-error.util';
import { UsersApiService } from './users-api.service';

const ROLE_HINTS: Record<TenantRole, string> = {
  OWNER: 'Tous les droits, y compris la gestion des autres admins.',
  ADMIN: 'Gère les utilisateurs, la messagerie et toutes les données.',
  USER: 'Crée et modifie clients, devis, factures.',
  READONLY: 'Consultation uniquement.',
};

/** Gestion des utilisateurs du compte par ses administrateurs : invitation, rôle, accès. */
@Component({
  selector: 'app-users-page',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, LoadingStateComponent, ErrorBannerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-8">
      <div>
        <h1 class="text-2xl font-bold text-white">Utilisateurs</h1>
        <p class="mt-1 text-sm text-slate-400">
          Les personnes ayant accès à ce compte, tous workspaces confondus. Un invité reçoit un lien pour choisir son mot de passe.
        </p>
      </div>

      <form class="rounded-xl border border-slate-800 bg-slate-900/50 p-6" [formGroup]="form" (ngSubmit)="invite()">
        <h2 class="text-lg font-semibold text-white">Inviter une personne</h2>
        <div class="mt-4 grid gap-4 sm:grid-cols-4">
          <label class="block text-sm text-slate-300 sm:col-span-2">
            E-mail
            <input type="email" formControlName="email" required autocomplete="off" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" placeholder="prenom.nom@entreprise.fr" />
          </label>
          <label class="block text-sm text-slate-300">
            Nom <span class="text-slate-500">(optionnel)</span>
            <input formControlName="displayName" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" />
          </label>
          <label class="block text-sm text-slate-300">
            Rôle
            <select formControlName="role" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white">
              @for (r of assignableRoles(); track r) {
                <option [value]="r">{{ roleLabel(r) }}</option>
              }
            </select>
          </label>
        </div>
        <p class="mt-2 text-xs text-slate-500">{{ roleHint(form.controls.role.value) }}</p>

        @if (inviteError()) {
          <app-error-banner class="mt-4 block" [message]="inviteError()!" />
        }
        @if (lastInvitation(); as inv) {
          <div class="mt-4 rounded-lg border px-4 py-3 text-sm" [class]="inv.emailSent ? 'border-emerald-500/30 bg-emerald-950/30 text-emerald-200' : 'border-amber-500/30 bg-amber-950/30 text-amber-100'">
            @if (inv.emailSent) {
              Invitation envoyée à <span class="font-medium">{{ inv.user.email }}</span>.
            } @else if (inv.activationUrl) {
              <p>Aucune boîte d’envoi n’est configurée chez l’éditeur : transmettez vous-même ce lien à <span class="font-medium">{{ inv.user.email }}</span> (valable 7 jours).</p>
              <div class="mt-2 flex flex-wrap items-center gap-2">
                <code class="max-w-full truncate rounded bg-black/30 px-2 py-1 text-xs">{{ inv.activationUrl }}</code>
                <button type="button" class="rounded-lg border border-amber-500/40 px-2.5 py-1 text-xs hover:bg-amber-900/40" (click)="copy(inv.activationUrl)">
                  {{ copied() ? 'Copié' : 'Copier' }}
                </button>
              </div>
            } @else {
              <span class="font-medium">{{ inv.user.email }}</span> a maintenant accès à ce compte avec son mot de passe habituel.
            }
          </div>
        }

        <div class="mt-5">
          <button type="submit" class="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500 disabled:opacity-50" [disabled]="busy() || form.invalid">
            Envoyer l’invitation
          </button>
        </div>
      </form>

      @if (error()) {
        <app-error-banner [message]="error()!" />
      }
      @if (actionError()) {
        <app-error-banner [message]="actionError()!" />
      }
      @if (loading() && !users().length) {
        <app-loading-state />
      } @else {
        <div class="overflow-x-auto rounded-xl border border-slate-800">
          <table class="min-w-full divide-y divide-slate-800 text-sm">
            <thead class="bg-slate-900/80 text-left text-xs uppercase text-slate-500">
              <tr>
                <th class="px-4 py-3">Utilisateur</th>
                <th class="px-4 py-3">Rôle</th>
                <th class="px-4 py-3">Statut</th>
                <th class="px-4 py-3">Dernière connexion</th>
                <th class="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800">
              @for (u of users(); track u.userId) {
                <tr [class.opacity-60]="!u.enabled">
                  <td class="px-4 py-3">
                    <p class="font-medium text-white">
                      {{ u.displayName }}
                      @if (u.self) {
                        <span class="ml-1 rounded-md border border-slate-600 px-1.5 py-0.5 text-[11px] text-slate-400">vous</span>
                      }
                    </p>
                    <p class="text-xs text-slate-500">{{ u.email }}</p>
                  </td>
                  <td class="px-4 py-3">
                    @if (canManage(u)) {
                      <select class="rounded-lg border border-slate-700 bg-slate-950 px-2 py-1 text-white" [value]="u.role" [disabled]="busy()" (change)="changeRole(u, $any($event.target).value)">
                        @for (r of assignableRoles(); track r) {
                          <option [value]="r">{{ roleLabel(r) }}</option>
                        }
                      </select>
                    } @else {
                      <span class="text-slate-300">{{ roleLabel(u.role) }}</span>
                    }
                  </td>
                  <td class="px-4 py-3">
                    @if (u.pending) {
                      <span class="rounded-full border border-amber-500/30 bg-amber-950/40 px-2 py-0.5 text-xs text-amber-300">Invitation en attente</span>
                    } @else if (u.enabled) {
                      <span class="rounded-full border border-emerald-500/30 bg-emerald-950/40 px-2 py-0.5 text-xs text-emerald-300">Actif</span>
                    } @else {
                      <span class="rounded-full border border-red-500/30 bg-red-950/40 px-2 py-0.5 text-xs text-red-300">Désactivé</span>
                    }
                  </td>
                  <td class="px-4 py-3 text-slate-400">
                    {{ u.lastLoginAt ? (u.lastLoginAt | date: 'dd/MM/yyyy HH:mm') : '—' }}
                  </td>
                  <td class="px-4 py-3 text-right">
                    @if (canManage(u)) {
                      <div class="flex flex-wrap justify-end gap-2">
                        @if (u.pending) {
                          <button type="button" class="rounded-lg border border-slate-700 px-2.5 py-1 text-xs text-slate-200 hover:bg-slate-800 disabled:opacity-50" [disabled]="busy()" (click)="resend(u)">
                            Renvoyer l’invitation
                          </button>
                        } @else {
                          <button type="button" class="rounded-lg border border-slate-700 px-2.5 py-1 text-xs text-slate-200 hover:bg-slate-800 disabled:opacity-50" [disabled]="busy()" (click)="toggleEnabled(u)">
                            {{ u.enabled ? 'Désactiver' : 'Réactiver' }}
                          </button>
                        }
                        <button type="button" class="rounded-lg px-2.5 py-1 text-xs text-red-300 hover:bg-red-950/40 disabled:opacity-50" [disabled]="busy()" (click)="remove(u)">
                          Retirer l’accès
                        </button>
                      </div>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
})
export class UsersPageComponent {
  private readonly api = inject(UsersApiService);
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  readonly users = signal<TenantUser[]>([]);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly inviteError = signal<string | null>(null);
  readonly lastInvitation = signal<InvitationResult | null>(null);
  readonly copied = signal(false);

  readonly roleLabel = roleLabel;

  /** Un ADMIN ne peut ni attribuer ni toucher au rôle Admin principal (OWNER). */
  readonly assignableRoles = computed<TenantRole[]>(() =>
    this.auth.profile()?.role === 'OWNER' ? ['OWNER', 'ADMIN', 'USER', 'READONLY'] : ['ADMIN', 'USER', 'READONLY'],
  );

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    displayName: ['', [Validators.maxLength(255)]],
    role: ['USER' as TenantRole, Validators.required],
  });

  constructor() {
    this.load();
  }

  roleHint(role: TenantRole): string {
    return ROLE_HINTS[role] ?? '';
  }

  canManage(u: TenantUser): boolean {
    if (u.self) {
      return false;
    }
    return u.role !== 'OWNER' || this.auth.profile()?.role === 'OWNER';
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (list) => this.users.set(list),
        error: (err) => this.error.set(apiErrorMessage(err, 'Impossible de charger les utilisateurs')),
      });
  }

  invite(): void {
    if (this.form.invalid || this.busy()) {
      return;
    }
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.inviteError.set(null);
    this.lastInvitation.set(null);
    this.copied.set(false);
    this.api
      .invite({ email: v.email.trim(), displayName: v.displayName.trim() || null, role: v.role })
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (res) => {
          this.lastInvitation.set(res);
          this.form.reset({ email: '', displayName: '', role: 'USER' });
          this.load();
        },
        error: (err) => this.inviteError.set(apiErrorMessage(err, 'Invitation impossible')),
      });
  }

  resend(u: TenantUser): void {
    this.busy.set(true);
    this.actionError.set(null);
    this.copied.set(false);
    this.api
      .resendInvitation(u.userId)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (res) => this.lastInvitation.set(res),
        error: (err) => this.actionError.set(apiErrorMessage(err, 'Renvoi impossible')),
      });
  }

  changeRole(u: TenantUser, role: TenantRole): void {
    if (role === u.role) {
      return;
    }
    this.patch(u, { role });
  }

  toggleEnabled(u: TenantUser): void {
    if (u.enabled && !confirm(`Désactiver le compte de ${u.displayName} ?\n\nLa personne ne pourra plus se connecter.`)) {
      return;
    }
    this.patch(u, { enabled: !u.enabled });
  }

  remove(u: TenantUser): void {
    if (!confirm(`Retirer l’accès de ${u.displayName} (${u.email}) à ce compte ?`)) {
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    this.api
      .remove(u.userId)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => this.users.update((list) => list.filter((x) => x.userId !== u.userId)),
        error: (err) => this.actionError.set(apiErrorMessage(err, 'Retrait impossible')),
      });
  }

  copy(value: string): void {
    void navigator.clipboard?.writeText(value).then(() => this.copied.set(true));
  }

  private patch(u: TenantUser, payload: { role?: TenantRole; enabled?: boolean }): void {
    this.busy.set(true);
    this.actionError.set(null);
    this.api
      .update(u.userId, payload)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (updated) => this.users.update((list) => list.map((x) => (x.userId === updated.userId ? updated : x))),
        error: (err) => {
          this.actionError.set(apiErrorMessage(err, 'Modification impossible'));
          this.load();
        },
      });
  }
}
