import { Component, inject, signal } from '@angular/core';
import { LayoutUiService } from './layout-ui.service';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { WorkspaceService } from '../workspace/workspace.service';
import { Workspace, WorkspaceUpdatePayload } from '../../shared/models/workspace.model';
import { AuthService } from '../auth/auth.service';
import { roleLabel } from '../../shared/models/auth.model';
import { workspaceUi } from '../workspace/workspace-ui.config';
import { SidebarNavModulesService } from './sidebar-nav-modules.service';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, LucideIconComponent, FormsModule],
  template: `
    @if (layout.mobileSidebarOpen()) {
      <button
        type="button"
        class="fixed inset-0 z-40 bg-black/60 md:hidden"
        aria-label="Fermer le menu"
        (click)="layout.closeMobileSidebar()"
      ></button>
    }
    <aside
      class="fixed inset-y-0 left-0 z-50 flex h-screen min-h-screen w-64 shrink-0 flex-col border-r border-slate-800 bg-[#0B0E11] transition-transform md:relative md:translate-x-0"
      [class.-translate-x-full]="!layout.mobileSidebarOpen()"
      [class.translate-x-0]="layout.mobileSidebarOpen()"
    >
      <div class="shrink-0 border-b border-slate-800 px-4 py-4">
        <a routerLink="/dashboard" class="block" (click)="layout.closeMobileSidebar()">
          <img
            src="/branding/nerd-group-logo.png"
            alt="NERD GROUP"
            class="h-auto w-full max-h-12 object-contain object-left"
            width="220"
            height="48"
          />
        </a>
      </div>

      <div class="min-h-0 flex-1 overflow-y-auto p-4">
        <div class="mb-2 flex items-center justify-between gap-2 px-3">
          <p class="text-xs font-semibold uppercase tracking-wider text-slate-500">Workspace</p>
          @if (auth.canAdminister()) {
            <button
              type="button"
              class="inline-flex size-7 items-center justify-center rounded-md text-slate-500 transition hover:bg-slate-800 hover:text-slate-200"
              [class.bg-slate-800]="workspaceManageMode()"
              [class.text-orange-300]="workspaceManageMode()"
              [attr.aria-pressed]="workspaceManageMode()"
              aria-label="Gérer les workspaces"
              (click)="workspaceManageMode.update((v) => !v)"
            >
              <app-lucide-icon name="settings" [size]="16" />
            </button>
          }
        </div>
        @if (auth.canAdminister() && workspaceManageMode()) {
          <div class="mb-2 flex items-center justify-between gap-2 px-3">
            <p class="text-[11px] leading-snug text-slate-600">Créer ou supprimer un workspace.</p>
            <button
              type="button"
              class="inline-flex shrink-0 items-center gap-1 rounded-md bg-slate-800 px-2 py-1 text-[11px] font-medium text-orange-300 hover:bg-slate-700"
              (click)="openCreateWorkspace()"
            >
              <app-lucide-icon name="plus" [size]="14" />
              Nouveau
            </button>
          </div>
        }
        <nav class="space-y-1" aria-label="Workspaces">
          @for (ws of workspaces.workspaces(); track ws.id) {
            <div
              class="flex items-center gap-1 rounded-lg transition-colors"
              [class.bg-slate-800]="workspaces.selectedId() === ws.id"
            >
              <button
                type="button"
                class="flex min-w-0 flex-1 items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm font-medium transition-colors"
                [class.text-white]="workspaces.selectedId() === ws.id"
                [class.text-slate-400]="workspaces.selectedId() !== ws.id"
                (click)="select(ws.id)"
              >
                <span class="h-2 w-2 shrink-0 rounded-full" [class]="ui(ws).dotClass"></span>
                <span class="truncate">{{ ws.name }}</span>
              </button>
              @if (auth.canAdminister()) {
                <button
                  type="button"
                  class="inline-flex size-8 shrink-0 items-center justify-center rounded-md text-slate-500 transition hover:bg-slate-700 hover:text-cyan-400"
                  [attr.aria-label]="'Paramètres émetteur — ' + ws.name"
                  [disabled]="workspaceBusy()"
                  (click)="openEditWorkspace(ws); $event.stopPropagation()"
                >
                  <app-lucide-icon name="settings" [size]="15" />
                </button>
              }
              @if (auth.canAdminister() && workspaceManageMode() && workspaces.workspaces().length > 1) {
                <button
                  type="button"
                  class="mr-1 inline-flex size-8 shrink-0 items-center justify-center rounded-md text-red-400/80 transition hover:bg-red-950/50 hover:text-red-300"
                  [attr.aria-label]="'Supprimer ' + ws.name"
                  [disabled]="workspaceBusy()"
                  (click)="deleteWorkspace(ws.id, ws.name)"
                >
                  <app-lucide-icon name="trash" [size]="15" />
                </button>
              }
            </div>
          }
        </nav>

        <div class="mb-2 mt-6 flex items-center justify-between gap-2 px-3">
          <p class="text-xs font-semibold uppercase tracking-wider text-slate-500">Modules</p>
          <button
            type="button"
            class="inline-flex size-7 items-center justify-center rounded-md text-slate-500 transition hover:bg-slate-800 hover:text-slate-200"
            [class.bg-slate-800]="navModules.customizeMode()"
            [class.text-orange-300]="navModules.customizeMode()"
            [attr.aria-pressed]="navModules.customizeMode()"
            aria-label="Personnaliser les modules affichés"
            (click)="navModules.toggleCustomizeMode()"
          >
            <app-lucide-icon name="settings" [size]="16" />
          </button>
        </div>

        @if (navModules.customizeMode()) {
          <p class="mb-2 px-3 text-[11px] leading-snug text-slate-600">
            Cliquez sur l’œil pour afficher ou masquer une rubrique.
          </p>
          <nav class="space-y-1" aria-label="Personnalisation des modules">
            @for (item of navModules.items(); track item.path) {
              <div
                class="flex items-center gap-2 rounded-lg px-3 py-2 text-sm"
                [class.bg-slate-900/50]="!navModules.isVisible(item.path)"
              >
                <span
                  class="min-w-0 flex-1 truncate font-medium"
                  [class.text-slate-200]="navModules.isVisible(item.path)"
                  [class.text-slate-600]="!navModules.isVisible(item.path)"
                >
                  {{ item.label }}
                </span>
                <button
                  type="button"
                  class="inline-flex size-8 shrink-0 items-center justify-center rounded-md text-slate-400 transition hover:bg-slate-800 hover:text-white"
                  [attr.aria-label]="
                    navModules.isVisible(item.path) ? 'Masquer ' + item.label : 'Afficher ' + item.label
                  "
                  (click)="navModules.toggleVisibility(item.path)"
                >
                  @if (navModules.isVisible(item.path)) {
                    <app-lucide-icon name="eye" [size]="16" />
                  } @else {
                    <app-lucide-icon name="eye-off" [size]="16" />
                  }
                </button>
              </div>
            }
          </nav>
        } @else {
          <nav class="space-y-1" aria-label="Navigation principale">
            @for (item of navModules.visibleItems(); track item.path) {
              <a
                [routerLink]="item.path"
                routerLinkActive="bg-slate-800/60 text-white"
                class="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-slate-400 transition-colors hover:bg-slate-800/50 hover:text-white"
                (click)="layout.closeMobileSidebar()"
              >
                {{ item.label }}
              </a>
            }
          </nav>
        }

        @if (auth.canAdminister()) {
          <p class="mb-2 mt-6 px-3 text-xs font-semibold uppercase tracking-wider text-slate-500">Paramètres</p>
          <nav class="space-y-1" aria-label="Paramètres du workspace">
            <a
              routerLink="/settings/mail"
              routerLinkActive="bg-slate-800/60 text-white"
              class="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-slate-400 transition-colors hover:bg-slate-800/50 hover:text-white"
              (click)="layout.closeMobileSidebar()"
            >
              <app-lucide-icon name="mail" [size]="16" />
              Messagerie
            </a>
            <a
              routerLink="/settings/users"
              routerLinkActive="bg-slate-800/60 text-white"
              class="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-slate-400 transition-colors hover:bg-slate-800/50 hover:text-white"
              (click)="layout.closeMobileSidebar()"
            >
              <app-lucide-icon name="users" [size]="16" />
              Utilisateurs
            </a>
          </nav>
        }

        @if (auth.isPlatformAdmin()) {
          <p class="mb-2 mt-6 px-3 text-xs font-semibold uppercase tracking-wider text-slate-500">Éditeur</p>
          <nav class="space-y-1" aria-label="Administration plateforme">
            <a
              routerLink="/admin/tenants"
              routerLinkActive="bg-slate-800/60 text-white"
              class="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-slate-400 transition-colors hover:bg-slate-800/50 hover:text-white"
              (click)="layout.closeMobileSidebar()"
            >
              <app-lucide-icon name="settings" [size]="16" />
              Comptes clients
            </a>
          </nav>
        }
      </div>

      <div class="mt-auto shrink-0 border-t border-slate-800 bg-[#0B0E11] p-4">
        <a routerLink="/settings/profile" class="group block" title="Mon profil" (click)="layout.closeMobileSidebar()">
          <p class="truncate text-sm font-medium text-white group-hover:text-brand-400">{{ auth.profile()?.displayName || auth.profile()?.email }}</p>
          <p class="truncate text-xs text-slate-400" [title]="auth.profile()?.email">{{ auth.profile()?.email }}</p>
          <p class="mt-0.5 text-xs text-slate-500">{{ roleLabel(auth.profile()?.role) }}</p>
        </a>
        <div class="mt-3 flex items-center gap-3 text-xs">
          <a routerLink="/settings/profile" class="text-slate-400 hover:text-white" (click)="layout.closeMobileSidebar()">Mon profil</a>
          <span class="text-slate-700">·</span>
          <button type="button" class="text-slate-400 hover:text-white" (click)="auth.logout()">Déconnexion</button>
        </div>
      </div>
    </aside>

    @if (editOpen()) {
      <div
        class="fixed inset-0 z-[70] flex items-center justify-center overflow-y-auto bg-black/80 p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="edit-ws-title"
      >
        <form
          class="my-4 w-full max-w-lg rounded-xl border border-slate-700 bg-[#0B0E11] p-5 shadow-xl"
          (ngSubmit)="submitEditWorkspace()"
        >
          <h2 id="edit-ws-title" class="text-lg font-semibold text-white">Prestataire / émetteur</h2>
          <p class="mt-1 text-sm text-slate-400">
            Coordonnées affichées sur les devis et factures — {{ editSlug() }}
          </p>

          <div class="mt-4 space-y-3">
            <label class="block text-sm text-slate-300">
              Raison sociale / nom commercial
              <input
                type="text"
                required
                maxlength="255"
                class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white"
                [(ngModel)]="editForm.name"
                name="editName"
                [disabled]="workspaceBusy()"
              />
            </label>
            <label class="block text-sm text-slate-300">
              Activité / sous-titre
              <input
                type="text"
                maxlength="255"
                placeholder="Ingénierie &amp; solutions digitales"
                class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white"
                [(ngModel)]="editForm.legalSubtitle"
                name="editSubtitle"
                [disabled]="workspaceBusy()"
              />
            </label>
          </div>

          <p class="mt-4 text-xs font-semibold uppercase tracking-wider text-cyan-500/90">Adresse</p>
          <div class="mt-2 space-y-3">
            <input
              type="text"
              maxlength="255"
              placeholder="Ligne d'adresse 1"
              class="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white"
              [(ngModel)]="editForm.addressLine1"
              name="editAddr1"
              [disabled]="workspaceBusy()"
            />
            <input
              type="text"
              maxlength="255"
              placeholder="Complément (optionnel)"
              class="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white"
              [(ngModel)]="editForm.addressLine2"
              name="editAddr2"
              [disabled]="workspaceBusy()"
            />
            <div class="grid grid-cols-2 gap-3">
              <input
                type="text"
                maxlength="20"
                placeholder="Code postal"
                class="rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white"
                [(ngModel)]="editForm.postalCode"
                name="editCp"
                [disabled]="workspaceBusy()"
              />
              <input
                type="text"
                maxlength="120"
                placeholder="Ville"
                class="rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white"
                [(ngModel)]="editForm.city"
                name="editCity"
                [disabled]="workspaceBusy()"
              />
            </div>
            <input
              type="text"
              maxlength="120"
              placeholder="Région / département"
              class="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white"
              [(ngModel)]="editForm.region"
              name="editRegion"
              [disabled]="workspaceBusy()"
            />
            <input
              type="text"
              maxlength="2"
              placeholder="FR"
              class="w-24 rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm uppercase text-white"
              [(ngModel)]="editForm.countryCode"
              name="editCountry"
              [disabled]="workspaceBusy()"
            />
          </div>

          <p class="mt-4 text-xs font-semibold uppercase tracking-wider text-cyan-500/90">Contact</p>
          <div class="mt-2 grid gap-3 sm:grid-cols-2">
            <label class="block text-sm text-slate-300 sm:col-span-2">
              E-mail
              <input
                type="email"
                maxlength="255"
                class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white"
                [(ngModel)]="editForm.contactEmail"
                name="editEmail"
                [disabled]="workspaceBusy()"
              />
            </label>
            <label class="block text-sm text-slate-300 sm:col-span-2">
              Site web
              <input
                type="url"
                maxlength="512"
                placeholder="https://…"
                class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white"
                [(ngModel)]="editForm.websiteUrl"
                name="editWeb"
                [disabled]="workspaceBusy()"
              />
            </label>
          </div>

          <p class="mt-4 text-xs font-semibold uppercase tracking-wider text-cyan-500/90">Identifiants légaux</p>
          <div class="mt-2 grid gap-3 sm:grid-cols-2">
            <label class="block text-sm text-slate-300">
              SIREN
              <input
                type="text"
                maxlength="20"
                class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 font-mono text-sm text-white"
                [(ngModel)]="editForm.siren"
                name="editSiren"
                [disabled]="workspaceBusy()"
              />
            </label>
            <label class="block text-sm text-slate-300">
              N° TVA intracom.
              <input
                type="text"
                maxlength="32"
                class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 font-mono text-sm text-white"
                [(ngModel)]="editForm.vatNumber"
                name="editVat"
                [disabled]="workspaceBusy()"
              />
            </label>
          </div>

          <p class="mt-4 text-xs font-semibold uppercase tracking-wider text-cyan-500/90">Coordonnées bancaires</p>
          <p class="mt-1 text-[11px] text-slate-500">Affichées dans les conditions de règlement des devis et factures.</p>
          <div class="mt-2 grid gap-3 sm:grid-cols-3">
            <label class="block text-sm text-slate-300 sm:col-span-2">
              IBAN
              <input
                type="text"
                maxlength="42"
                placeholder="FR76 1234 5678 9012 3456 7890 123"
                autocomplete="off"
                class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 font-mono text-sm uppercase text-white"
                [(ngModel)]="editForm.iban"
                name="editIban"
                [disabled]="workspaceBusy()"
              />
            </label>
            <label class="block text-sm text-slate-300">
              BIC / SWIFT
              <input
                type="text"
                maxlength="11"
                placeholder="NERDFRPPXXX"
                autocomplete="off"
                class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 font-mono text-sm uppercase text-white"
                [(ngModel)]="editForm.bic"
                name="editBic"
                [disabled]="workspaceBusy()"
              />
            </label>
          </div>

          <p class="mt-4 text-xs font-semibold uppercase tracking-wider text-cyan-500/90">Numérotation documents</p>
          <div class="mt-2 grid gap-3 sm:grid-cols-3">
            <label class="block text-sm text-slate-300">
              Devise
              <input
                type="text"
                required
                maxlength="3"
                class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 font-mono text-sm uppercase text-white"
                [(ngModel)]="editForm.currencyCode"
                name="editCurrency"
                [disabled]="workspaceBusy()"
              />
            </label>
            <label class="block text-sm text-slate-300">
              Préfixe devis
              <input
                type="text"
                required
                maxlength="20"
                class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 font-mono text-sm text-white"
                [(ngModel)]="editForm.quotationPrefix"
                name="editQPrefix"
                [disabled]="workspaceBusy()"
              />
            </label>
            <label class="block text-sm text-slate-300">
              Préfixe facture
              <input
                type="text"
                required
                maxlength="20"
                class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 font-mono text-sm text-white"
                [(ngModel)]="editForm.invoicePrefix"
                name="editIPrefix"
                [disabled]="workspaceBusy()"
              />
            </label>
          </div>

          @if (editError()) {
            <p class="mt-3 text-sm text-red-400">{{ editError() }}</p>
          }
          <div class="mt-5 flex justify-end gap-2">
            <button
              type="button"
              class="rounded-lg px-4 py-2 text-sm text-slate-400 hover:text-white"
              [disabled]="workspaceBusy()"
              (click)="closeEditWorkspace()"
            >
              Annuler
            </button>
            <button
              type="submit"
              class="rounded-lg bg-cyan-600 px-4 py-2 text-sm font-medium text-white hover:bg-cyan-500 disabled:opacity-50"
              [disabled]="workspaceBusy() || !editForm.name.trim()"
            >
              Enregistrer
            </button>
          </div>
        </form>
      </div>
    }

    @if (createOpen()) {
      <div
        class="fixed inset-0 z-[70] flex items-center justify-center bg-black/80 p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-ws-title"
      >
        <form
          class="w-full max-w-md rounded-xl border border-slate-700 bg-[#0B0E11] p-5 shadow-xl"
          (ngSubmit)="submitCreateWorkspace()"
        >
          <h2 id="create-ws-title" class="text-lg font-semibold text-white">Nouveau workspace</h2>
          <p class="mt-1 text-sm text-slate-400">Clients, produits et factures seront isolés dans ce périmètre.</p>
          <label class="mt-4 block text-sm text-slate-300">
            Nom
            <input
              type="text"
              name="wsName"
              required
              maxlength="255"
              class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-white"
              [(ngModel)]="createName"
              [disabled]="workspaceBusy()"
            />
          </label>
          <label class="mt-3 block text-sm text-slate-300">
            Slug <span class="text-slate-500">(optionnel)</span>
            <input
              type="text"
              name="wsSlug"
              maxlength="100"
              placeholder="mon-atelier"
              class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-white"
              [(ngModel)]="createSlug"
              [disabled]="workspaceBusy()"
            />
          </label>
          @if (createError()) {
            <p class="mt-3 text-sm text-red-400">{{ createError() }}</p>
          }
          <div class="mt-5 flex justify-end gap-2">
            <button
              type="button"
              class="rounded-lg px-4 py-2 text-sm text-slate-400 hover:text-white"
              [disabled]="workspaceBusy()"
              (click)="closeCreateWorkspace()"
            >
              Annuler
            </button>
            <button
              type="submit"
              class="rounded-lg bg-orange-600 px-4 py-2 text-sm font-medium text-white hover:bg-orange-500 disabled:opacity-50"
              [disabled]="workspaceBusy() || !createName.trim()"
            >
              Créer
            </button>
          </div>
        </form>
      </div>
    }
  `,
})
export class SidebarComponent {
  readonly workspaces = inject(WorkspaceService);
  readonly auth = inject(AuthService);
  readonly roleLabel = roleLabel;
  readonly layout = inject(LayoutUiService);
  readonly navModules = inject(SidebarNavModulesService);

  readonly workspaceManageMode = signal(false);
  readonly createOpen = signal(false);
  readonly editOpen = signal(false);
  readonly editWorkspaceId = signal<string | null>(null);
  readonly editSlug = signal('');
  readonly workspaceBusy = signal(false);
  readonly createError = signal<string | null>(null);
  readonly editError = signal<string | null>(null);
  createName = '';
  createSlug = '';
  editForm: WorkspaceUpdatePayload = this.emptyEditForm();

  private emptyEditForm(): WorkspaceUpdatePayload {
    return {
      name: '',
      legalSubtitle: '',
      addressLine1: '',
      addressLine2: '',
      postalCode: '',
      city: '',
      region: '',
      countryCode: 'FR',
      contactEmail: '',
      websiteUrl: '',
      siren: '',
      vatNumber: '',
      iban: '',
      bic: '',
      currencyCode: 'EUR',
      invoicePrefix: 'FAC',
      quotationPrefix: 'DEV',
    };
  }

  ui(ws: { slug: string; name: string }) {
    return workspaceUi(ws.slug, ws.name);
  }

  select(id: string): void {
    this.workspaces.selectWorkspace(id);
    this.layout.closeMobileSidebar();
  }

  openEditWorkspace(ws: Workspace): void {
    this.editWorkspaceId.set(ws.id);
    this.editSlug.set(ws.slug);
    this.editError.set(null);
    this.editForm = {
      name: ws.name,
      legalSubtitle: ws.legalSubtitle ?? '',
      addressLine1: ws.addressLine1 ?? '',
      addressLine2: ws.addressLine2 ?? '',
      postalCode: ws.postalCode ?? '',
      city: ws.city ?? '',
      region: ws.region ?? '',
      countryCode: ws.countryCode || 'FR',
      contactEmail: ws.contactEmail ?? '',
      websiteUrl: ws.websiteUrl ?? '',
      siren: ws.siren ?? '',
      vatNumber: ws.vatNumber ?? '',
      iban: ws.iban ?? '',
      bic: ws.bic ?? '',
      currencyCode: ws.currencyCode || 'EUR',
      invoicePrefix: ws.invoicePrefix || 'FAC',
      quotationPrefix: ws.quotationPrefix || 'DEV',
    };
    this.editOpen.set(true);
  }

  closeEditWorkspace(): void {
    if (this.workspaceBusy()) {
      return;
    }
    this.editOpen.set(false);
    this.editWorkspaceId.set(null);
  }

  submitEditWorkspace(): void {
    const id = this.editWorkspaceId();
    if (!id || this.workspaceBusy() || !this.editForm.name.trim()) {
      return;
    }
    const payload: WorkspaceUpdatePayload = {
      name: this.editForm.name.trim(),
      legalSubtitle: this.nullIfBlank(this.editForm.legalSubtitle),
      addressLine1: this.nullIfBlank(this.editForm.addressLine1),
      addressLine2: this.nullIfBlank(this.editForm.addressLine2),
      postalCode: this.nullIfBlank(this.editForm.postalCode),
      city: this.nullIfBlank(this.editForm.city),
      region: this.nullIfBlank(this.editForm.region),
      countryCode: (this.editForm.countryCode || 'FR').trim().toUpperCase(),
      contactEmail: this.nullIfBlank(this.editForm.contactEmail),
      websiteUrl: this.nullIfBlank(this.editForm.websiteUrl),
      siren: this.nullIfBlank(this.editForm.siren),
      vatNumber: this.nullIfBlank(this.editForm.vatNumber),
      iban: this.nullIfBlank(this.editForm.iban?.replace(/\s+/g, '').toUpperCase()),
      bic: this.nullIfBlank(this.editForm.bic?.replace(/\s+/g, '').toUpperCase()),
      currencyCode: this.editForm.currencyCode.trim().toUpperCase(),
      invoicePrefix: this.editForm.invoicePrefix.trim(),
      quotationPrefix: this.editForm.quotationPrefix.trim(),
    };
    this.workspaceBusy.set(true);
    this.editError.set(null);
    this.workspaces
      .updateWorkspace(id, payload)
      .pipe(finalize(() => this.workspaceBusy.set(false)))
      .subscribe({
        next: () => this.editOpen.set(false),
        error: (err) => {
          this.editError.set(apiErrorMessage(err, 'Mise à jour impossible'));
        },
      });
  }

  private nullIfBlank(value: string | null | undefined): string | null {
    const t = value?.trim();
    return t ? t : null;
  }

  openCreateWorkspace(): void {
    this.createName = '';
    this.createSlug = '';
    this.createError.set(null);
    this.createOpen.set(true);
  }

  closeCreateWorkspace(): void {
    if (this.workspaceBusy()) {
      return;
    }
    this.createOpen.set(false);
  }

  submitCreateWorkspace(): void {
    const name = this.createName.trim();
    if (!name || this.workspaceBusy()) {
      return;
    }
    const slug = this.createSlug.trim();
    this.workspaceBusy.set(true);
    this.createError.set(null);
    this.workspaces
      .createWorkspace(slug ? { name, slug } : { name })
      .pipe(finalize(() => this.workspaceBusy.set(false)))
      .subscribe({
        next: () => {
          this.createOpen.set(false);
          this.layout.closeMobileSidebar();
        },
        error: (err) => {
          this.createError.set(apiErrorMessage(err, 'Impossible de créer le workspace'));
        },
      });
  }

  deleteWorkspace(id: string, name: string): void {
    if (this.workspaceBusy()) {
      return;
    }
    const ok = confirm(
      `Supprimer le workspace « ${name} » ?\n\nTous les clients, produits, devis, factures, paiements et contrats associés seront définitivement supprimés.`,
    );
    if (!ok) {
      return;
    }
    this.workspaceBusy.set(true);
    this.workspaces
      .deleteWorkspace(id)
      .pipe(finalize(() => this.workspaceBusy.set(false)))
      .subscribe({
        error: (err) => {
          alert(apiErrorMessage(err, 'Impossible de supprimer le workspace'));
        },
      });
  }
}
