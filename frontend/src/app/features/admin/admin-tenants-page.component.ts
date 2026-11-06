import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, finalize } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MODULES, moduleLabel } from '../../core/modules/module.service';
import { AdminTenantSummary } from '../../shared/models/admin.model';
import { ModuleCode } from '../../shared/models/auth.model';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { PaginatorComponent } from '../../shared/ui/paginator.component';
import { apiErrorMessage } from '../../shared/utils/api-error.util';
import { PageState } from '../../shared/utils/page-state';
import { AdminApiService } from './admin-api.service';

@Component({
  selector: 'app-admin-tenants-page',
  standalone: true,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    LoadingStateComponent,
    EmptyStateComponent,
    ErrorBannerComponent,
    PaginatorComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-6">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p class="text-xs font-semibold uppercase tracking-wider text-orange-400">Éditeur</p>
          <h1 class="text-2xl font-bold text-white">Comptes clients</h1>
        </div>
        <button
          type="button"
          class="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500"
          (click)="openCreate()"
        >
          Nouveau compte
        </button>
      </div>

      <div class="flex flex-wrap items-center gap-3">
        <label class="sr-only" for="admin-tenant-search">Rechercher</label>
        <input
          id="admin-tenant-search"
          type="search"
          placeholder="Rechercher un compte (nom, slug)…"
          class="w-full max-w-sm rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
          [value]="q()"
          (input)="onSearch($any($event.target).value)"
        />
        <span class="text-xs text-slate-500">{{ page.meta().totalElements }} compte(s)</span>
      </div>

      @if (error()) {
        <app-error-banner [message]="error()!" />
      }
      @if (loading() && !items().length) {
        <app-loading-state />
      } @else if (!items().length) {
        <app-empty-state title="Aucun compte client." />
      } @else {
        <div class="overflow-hidden rounded-xl border border-slate-800">
          <table class="min-w-full divide-y divide-slate-800 text-sm">
            <thead class="bg-slate-900/80 text-left text-xs uppercase text-slate-500">
              <tr>
                <th class="px-4 py-3">Compte</th>
                <th class="px-4 py-3">Slug</th>
                <th class="px-4 py-3">Statut</th>
                <th class="px-4 py-3">Utilisateurs</th>
                <th class="px-4 py-3">Modules actifs</th>
                <th class="px-4 py-3">Créé le</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800">
              @for (t of items(); track t.id) {
                <tr class="cursor-pointer hover:bg-slate-900/60" (click)="open(t)">
                  <td class="px-4 py-3 font-medium text-white">
                    <a [routerLink]="['/admin/tenants', t.id]" class="hover:underline" (click)="$event.stopPropagation()">
                      {{ t.name }}
                    </a>
                  </td>
                  <td class="px-4 py-3 font-mono text-xs text-slate-400">{{ t.slug }}</td>
                  <td class="px-4 py-3">
                    @if (t.active) {
                      <span class="rounded-full border border-emerald-500/30 bg-emerald-950/40 px-2 py-0.5 text-xs text-emerald-300">Actif</span>
                    } @else {
                      <span class="rounded-full border border-red-500/30 bg-red-950/40 px-2 py-0.5 text-xs text-red-300">Suspendu</span>
                    }
                  </td>
                  <td class="px-4 py-3 text-slate-300">{{ t.userCount }}</td>
                  <td class="px-4 py-3">
                    @if (!t.activeModules.length) {
                      <span class="text-xs text-slate-600">Socle uniquement</span>
                    }
                    @for (m of t.activeModules; track m) {
                      <span class="mr-1 rounded-md border border-slate-700 bg-slate-900 px-2 py-0.5 text-xs text-slate-300">{{ label(m) }}</span>
                    }
                  </td>
                  <td class="px-4 py-3 text-slate-400">{{ t.createdAt | date: 'dd/MM/yyyy' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-paginator
          [meta]="page.meta()"
          [disabled]="loading()"
          (pageChange)="page.goTo($event); load()"
          (pageSizeChange)="page.setSize($event); load()"
        />
      }
    </div>

    @if (createOpen()) {
      <div class="fixed inset-0 z-[70] flex items-center justify-center bg-black/80 p-4" role="dialog" aria-modal="true" aria-labelledby="create-tenant-title">
        <form class="w-full max-w-lg rounded-xl border border-slate-700 bg-[#0B0E11] p-5 shadow-xl" [formGroup]="form" (ngSubmit)="submitCreate()">
          <h2 id="create-tenant-title" class="text-lg font-semibold text-white">Nouveau compte client</h2>
          <p class="mt-1 text-sm text-slate-400">
            Crée le compte, son organisation, un premier workspace et l’utilisateur propriétaire.
          </p>

          <div class="mt-4 grid gap-3 sm:grid-cols-2">
            <label class="block text-sm text-slate-300 sm:col-span-2">
              Nom du compte
              <input formControlName="name" required maxlength="255" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-white" />
            </label>
            <label class="block text-sm text-slate-300">
              Slug <span class="text-slate-500">(optionnel)</span>
              <input formControlName="slug" maxlength="100" placeholder="mon-client" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-white" />
            </label>
            <label class="block text-sm text-slate-300">
              Raison sociale <span class="text-slate-500">(optionnel)</span>
              <input formControlName="legalName" maxlength="255" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-white" />
            </label>
            <label class="block text-sm text-slate-300 sm:col-span-2">
              Nom du premier workspace <span class="text-slate-500">(défaut : nom du compte)</span>
              <input formControlName="workspaceName" maxlength="255" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-white" />
            </label>
          </div>

          <h3 class="mt-5 text-sm font-semibold text-white">Propriétaire du compte</h3>
          <div class="mt-2 grid gap-3 sm:grid-cols-2">
            <label class="block text-sm text-slate-300">
              E-mail
              <input type="email" formControlName="ownerEmail" required maxlength="255" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-white" />
            </label>
            <label class="block text-sm text-slate-300">
              Nom affiché
              <input formControlName="ownerDisplayName" maxlength="255" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-white" />
            </label>
            <label class="block text-sm text-slate-300 sm:col-span-2">
              Mot de passe initial <span class="text-slate-500">(optionnel : sans mot de passe, le propriétaire reçoit une invitation par e-mail)</span>
              <input type="password" formControlName="ownerPassword" autocomplete="new-password" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-white" />
            </label>
          </div>

          <h3 class="mt-5 text-sm font-semibold text-white">Modules activés</h3>
          <div class="mt-2 space-y-2">
            @for (m of modules; track m.code) {
              <label class="flex cursor-pointer items-start gap-3 rounded-lg border border-slate-800 bg-slate-900/40 p-3 text-sm">
                <input type="checkbox" class="mt-0.5" [checked]="selectedModules().has(m.code)" (change)="toggleModule(m.code)" />
                <span>
                  <span class="block font-medium text-white">{{ m.label }}</span>
                  <span class="block text-xs text-slate-500">{{ m.description }}</span>
                </span>
              </label>
            }
          </div>

          @if (createError()) {
            <p class="mt-3 text-sm text-red-400">{{ createError() }}</p>
          }
          <div class="mt-5 flex justify-end gap-2">
            <button type="button" class="rounded-lg px-4 py-2 text-sm text-slate-400 hover:text-white" [disabled]="saving()" (click)="closeCreate()">
              Annuler
            </button>
            <button type="submit" class="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500 disabled:opacity-50" [disabled]="saving() || form.invalid">
              Créer le compte
            </button>
          </div>
        </form>
      </div>
    }
  `,
})
export class AdminTenantsPageComponent {
  private readonly api = inject(AdminApiService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  readonly modules = MODULES;
  readonly page = new PageState();
  readonly items = signal<AdminTenantSummary[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly q = signal('');

  readonly createOpen = signal(false);
  readonly saving = signal(false);
  readonly createError = signal<string | null>(null);
  readonly selectedModules = signal<ReadonlySet<ModuleCode>>(new Set());

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    slug: ['', [Validators.maxLength(100)]],
    legalName: ['', [Validators.maxLength(255)]],
    workspaceName: ['', [Validators.maxLength(255)]],
    ownerEmail: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    ownerDisplayName: ['', [Validators.maxLength(255)]],
    ownerPassword: ['', [Validators.minLength(8), Validators.maxLength(128)]],
  });

  private readonly search$ = new Subject<string>();

  constructor() {
    this.search$
      .pipe(debounceTime(250), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((value) => {
        this.q.set(value);
        this.page.reset();
        this.load();
      });
    this.load();
  }

  label(code: ModuleCode): string {
    return moduleLabel(code);
  }

  onSearch(value: string): void {
    this.search$.next(value.trim());
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .listTenants({ q: this.q() || undefined, ...this.page.query('name,asc') })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (res) => {
          this.items.set(res.content);
          this.page.apply(res);
        },
        error: (err) => this.error.set(apiErrorMessage(err, 'Impossible de charger les comptes')),
      });
  }

  open(t: AdminTenantSummary): void {
    void this.router.navigate(['/admin/tenants', t.id]);
  }

  openCreate(): void {
    this.form.reset();
    this.selectedModules.set(new Set());
    this.createError.set(null);
    this.createOpen.set(true);
  }

  closeCreate(): void {
    if (!this.saving()) {
      this.createOpen.set(false);
    }
  }

  toggleModule(code: ModuleCode): void {
    const next = new Set(this.selectedModules());
    if (next.has(code)) {
      next.delete(code);
    } else {
      next.add(code);
    }
    this.selectedModules.set(next);
  }

  submitCreate(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.createError.set(null);
    this.api
      .createTenant({
        name: v.name.trim(),
        slug: v.slug.trim() || null,
        legalName: v.legalName.trim() || null,
        workspaceName: v.workspaceName.trim() || null,
        ownerEmail: v.ownerEmail.trim(),
        ownerDisplayName: v.ownerDisplayName.trim() || null,
        ownerPassword: v.ownerPassword || null,
        modules: [...this.selectedModules()],
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (created) => {
          this.createOpen.set(false);
          // L'invitation éventuelle (lien à transmettre) est affichée par la page détail.
          void this.router.navigate(['/admin/tenants', created.id], { state: { invitation: created.invitation } });
        },
        error: (err) => this.createError.set(apiErrorMessage(err, 'Création impossible')),
      });
  }
}
