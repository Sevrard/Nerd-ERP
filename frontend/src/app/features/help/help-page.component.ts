import { Component, computed, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { OnboardingChecklist } from '../../shared/models/onboarding.model';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { apiErrorMessage } from '../../shared/utils/api-error.util';
import { AccountApiService } from '../auth/account-api.service';

interface HelpTopic {
  title: string;
  body: string;
  route?: string;
}

const TOPICS: HelpTopic[] = [
  {
    title: 'Workspaces',
    body: 'Chaque workspace (ex. services, boutique) a ses propres clients, documents et numérotation. Changez de workspace depuis la barre latérale ; la roue crantée ouvre ses réglages (mentions légales, IBAN, préfixes et motif de numérotation).',
  },
  {
    title: 'Clients & CRM',
    body: 'Créez vos clients un par un ou importez-les en masse (bouton « Importer CSV », modèle téléchargeable, simulation avant import). Planifiez des relances depuis la fiche client.',
    route: '/customers',
  },
  {
    title: 'Catalogue',
    body: 'Produits, catégories et packs. Les produits ont un mode de facturation (unité, jour, forfait, récurrent), un prix HT et un taux de TVA. Import CSV disponible, les catégories manquantes sont créées à la volée.',
    route: '/products',
  },
  {
    title: 'Devis',
    body: 'Remise par ligne et remise globale, acompte en %, récapitulatif de TVA par taux. Envoyez le devis par e-mail avec un lien d’acceptation public, dupliquez-le pour repartir d’un modèle, puis facturez l’acompte et le solde ou convertissez-le en facture.',
    route: '/quotations',
  },
  {
    title: 'Factures & paiements',
    body: 'Une facture validée n’est plus modifiable : corrigez par avoir. Enregistrez les paiements (partiels ou complets), relancez les impayés et exportez votre journal de ventes en CSV pour votre comptable.',
    route: '/invoices',
  },
  {
    title: 'Messagerie',
    body: 'Configurez une boîte d’envoi par workspace (SMTP ou mot de passe d’application Gmail) pour envoyer devis, factures et relances directement depuis l’application.',
    route: '/settings/mail',
  },
];

/** Guide de démarrage : checklist de configuration (calculée côté serveur) + repères fonctionnels. */
@Component({
  selector: 'app-help-page',
  standalone: true,
  imports: [RouterLink, ErrorBannerComponent, LoadingStateComponent, LucideIconComponent],
  template: `
    <div class="space-y-8 pb-10">
      <div class="border-b border-slate-800/80 pb-5">
        <h1 class="text-lg font-bold tracking-tight text-white">Guide de démarrage</h1>
        <p class="text-xs text-slate-400">Les étapes clés pour être opérationnel sur « {{ workspaces.selectedWorkspace()?.name ?? 'ce workspace' }} »</p>
      </div>

      @if (error()) {
        <app-error-banner [message]="error()!" />
      }

      <section class="rounded-xl border border-slate-800 bg-slate-900/70 p-6">
        @if (loading() && !checklist()) {
          <app-loading-state />
        } @else if (checklist(); as c) {
          <div class="flex flex-wrap items-center justify-between gap-3">
            <h2 class="text-base font-semibold text-white">
              @if (c.done === c.total) {
                Tout est prêt
              } @else {
                {{ c.done }} / {{ c.total }} étapes terminées
              }
            </h2>
            <span class="text-xs text-slate-500">{{ progress() }} %</span>
          </div>
          <div class="mt-3 h-2 overflow-hidden rounded-full bg-slate-800">
            <div class="h-full rounded-full bg-gradient-to-r from-emerald-500 to-teal-400 transition-all" [style.width.%]="progress()"></div>
          </div>
          <ol class="mt-5 divide-y divide-slate-800/60">
            @for (s of c.steps; track s.code) {
              <li class="flex items-start gap-3 py-3">
                <span
                  class="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border"
                  [class]="s.done ? 'border-emerald-500/40 bg-emerald-500/15 text-emerald-400' : 'border-slate-700 text-slate-600'"
                >
                  @if (s.done) {
                    <app-lucide-icon name="check" [size]="14" />
                  }
                </span>
                <div class="min-w-0 flex-1">
                  <p class="text-sm font-medium" [class]="s.done ? 'text-slate-400 line-through decoration-slate-600' : 'text-white'">{{ s.label }}</p>
                  <p class="mt-0.5 text-xs text-slate-500">{{ s.description }}</p>
                </div>
                @if (!s.done && s.route && (s.code !== 'MAIL' || auth.canAdminister())) {
                  <a [routerLink]="s.route" class="shrink-0 rounded-lg border border-slate-700 px-3 py-1.5 text-xs font-medium text-slate-200 hover:bg-slate-800">
                    Y aller
                  </a>
                }
              </li>
            }
          </ol>
        }
      </section>

      <section>
        <h2 class="mb-3 text-base font-semibold text-white">Repères</h2>
        <div class="grid gap-4 md:grid-cols-2">
          @for (t of topics; track t.title) {
            <article class="rounded-xl border border-slate-800 bg-slate-900/50 p-5">
              <h3 class="text-sm font-semibold text-white">{{ t.title }}</h3>
              <p class="mt-2 text-sm leading-relaxed text-slate-400">{{ t.body }}</p>
              @if (t.route) {
                <a [routerLink]="t.route" class="mt-3 inline-block text-xs font-medium text-brand-400 hover:text-brand-500">Ouvrir →</a>
              }
            </article>
          }
        </div>
      </section>

      <p class="text-xs text-slate-500">
        <a routerLink="/legal/cgu" class="hover:text-slate-300">Conditions d'utilisation</a> ·
        <a routerLink="/legal/cgv" class="hover:text-slate-300">Conditions de vente</a>
      </p>
    </div>
  `,
})
export class HelpPageComponent {
  readonly workspaces = inject(WorkspaceService);
  readonly auth = inject(AuthService);
  private readonly account = inject(AccountApiService);

  readonly topics = TOPICS;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly checklist = signal<OnboardingChecklist | null>(null);
  readonly progress = computed(() => {
    const c = this.checklist();
    return c && c.total ? Math.round((c.done / c.total) * 100) : 0;
  });

  constructor() {
    effect(() => {
      this.workspaces.generation();
      // Rechargée aussi quand les CGU viennent d'être acceptées (profil mis à jour).
      this.auth.profile()?.terms?.accepted;
      this.load();
    });
  }

  private load(): void {
    const workspaceId = this.workspaces.selectedId();
    if (!workspaceId) {
      return;
    }
    const gen = this.workspaces.generation();
    this.loading.set(true);
    this.error.set(null);
    this.account.onboardingChecklist(workspaceId).subscribe({
      next: (c) => {
        if (this.workspaces.isStale(gen)) {
          return;
        }
        this.checklist.set(c);
        this.loading.set(false);
      },
      error: (err) => {
        if (this.workspaces.isStale(gen)) {
          return;
        }
        this.error.set(apiErrorMessage(err, 'Chargement impossible'));
        this.loading.set(false);
      },
    });
  }
}
