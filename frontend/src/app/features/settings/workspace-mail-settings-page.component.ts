import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import {
  EmailLogEntry,
  SMTP_PRESETS,
  SmtpSecurity,
  WorkspaceMailSettings,
} from '../../shared/models/mail.model';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { PaginatorComponent } from '../../shared/ui/paginator.component';
import { apiErrorMessage } from '../../shared/utils/api-error.util';
import { PageState } from '../../shared/utils/page-state';
import { MailApiService } from './mail-api.service';

const KIND_LABELS: Record<EmailLogEntry['kind'], string> = {
  TEST: 'Test',
  ACCOUNT: 'Compte',
  QUOTATION: 'Devis',
  INVOICE: 'Facture',
  REMINDER: 'Relance',
};

@Component({
  selector: 'app-workspace-mail-settings-page',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, LoadingStateComponent, ErrorBannerComponent, PaginatorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-8">
      <div>
        <h1 class="text-2xl font-bold text-white">Messagerie</h1>
        <p class="mt-1 text-sm text-slate-400">
          Les devis et factures de « {{ workspaces.selectedWorkspace()?.name }} » sont envoyés depuis votre propre
          boîte e-mail (Gmail, OVH…). Vos clients vous répondent directement.
        </p>
      </div>

      @if (loadError()) {
        <app-error-banner [message]="loadError()!" />
      }
      @if (loading() && !settings()) {
        <app-loading-state />
      } @else if (settings(); as s) {
        <div class="flex flex-wrap items-center gap-3 text-sm">
          @if (s.configured && s.enabled) {
            <span class="rounded-full border border-emerald-500/30 bg-emerald-950/40 px-2.5 py-0.5 text-emerald-300">Boîte configurée — {{ s.fromEmail }}</span>
          } @else if (s.configured) {
            <span class="rounded-full border border-amber-500/30 bg-amber-950/40 px-2.5 py-0.5 text-amber-300">Configurée mais désactivée</span>
          } @else {
            <span class="rounded-full border border-slate-600 bg-slate-900 px-2.5 py-0.5 text-slate-300">Aucune boîte configurée</span>
          }
          @if (!s.configured || !s.enabled) {
            @if (s.platformFallbackAvailable) {
              <span class="text-slate-500">Les envois utiliseront la boîte de l’éditeur en attendant.</span>
            } @else {
              <span class="text-amber-400">Aucun envoi possible tant qu’une boîte n’est pas configurée.</span>
            }
          }
          @if (s.lastTestAt) {
            <span class="text-slate-500">
              Dernier test {{ s.lastTestAt | date: 'dd/MM/yyyy HH:mm' }} :
              @if (s.lastTestOk) { <span class="text-emerald-300">réussi</span> } @else { <span class="text-red-300">échec</span> }
            </span>
          }
        </div>

        <form class="rounded-xl border border-slate-800 bg-slate-900/50 p-6" [formGroup]="form" (ngSubmit)="save()">
          <h2 class="text-lg font-semibold text-white">Serveur d’envoi (SMTP)</h2>

          <div class="mt-4 flex flex-wrap gap-2">
            @for (p of presets; track p.id) {
              <button
                type="button"
                class="rounded-lg border px-3 py-1.5 text-xs font-medium transition"
                [class.border-brand-500]="preset() === p.id"
                [class.text-white]="preset() === p.id"
                [class.border-slate-700]="preset() !== p.id"
                [class.text-slate-400]="preset() !== p.id"
                (click)="applyPreset(p.id)"
              >
                {{ p.label }}
              </button>
            }
          </div>
          <p class="mt-2 text-xs text-slate-500">{{ presetHint() }}</p>

          <div class="mt-5 grid gap-4 sm:grid-cols-3">
            <label class="block text-sm text-slate-300 sm:col-span-2">
              Serveur
              <input formControlName="host" required class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" placeholder="smtp.gmail.com" />
            </label>
            <label class="block text-sm text-slate-300">
              Port
              <input type="number" formControlName="port" required min="1" max="65535" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" />
            </label>
            <label class="block text-sm text-slate-300">
              Sécurité
              <select formControlName="security" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white">
                <option value="STARTTLS">STARTTLS (port 587)</option>
                <option value="SSL">SSL/TLS (port 465)</option>
                <option value="NONE">Aucune (test local)</option>
              </select>
            </label>
            <label class="block text-sm text-slate-300 sm:col-span-2">
              Identifiant (adresse e-mail)
              <input formControlName="username" required autocomplete="username" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" placeholder="contact@mon-entreprise.fr" />
            </label>
            <label class="block text-sm text-slate-300 sm:col-span-3">
              Mot de passe
              @if (s.passwordSet) {
                <span class="text-slate-500">(enregistré — laissez vide pour le conserver)</span>
              } @else {
                <span class="text-slate-500">(pour Gmail : mot de passe d’application, pas celui du compte)</span>
              }
              <input type="password" formControlName="password" autocomplete="new-password" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" />
            </label>
            @if (appPasswordGuide(); as guide) {
              <div class="rounded-lg border border-slate-700 bg-slate-950/60 p-4 sm:col-span-3">
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <ol class="list-decimal space-y-1 pl-5 text-xs text-slate-400">
                    @for (step of guide.steps; track $index) {
                      <li>{{ step }}</li>
                    }
                  </ol>
                  <a
                    [href]="guide.url"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="inline-flex shrink-0 items-center gap-2 rounded-lg border border-brand-500/50 bg-brand-600/20 px-3 py-2 text-sm font-medium text-white hover:bg-brand-600/40"
                  >
                    {{ guide.buttonLabel }}
                    <span aria-hidden="true">↗</span>
                  </a>
                </div>
              </div>
            }
          </div>

          <h2 class="mt-7 text-lg font-semibold text-white">Expéditeur</h2>
          <div class="mt-4 grid gap-4 sm:grid-cols-3">
            <label class="block text-sm text-slate-300">
              Adresse d’envoi
              <input type="email" formControlName="fromEmail" required class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" />
            </label>
            <label class="block text-sm text-slate-300">
              Nom affiché
              <input formControlName="fromName" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" placeholder="Mon Entreprise" />
            </label>
            <label class="block text-sm text-slate-300">
              Adresse de réponse <span class="text-slate-500">(optionnel)</span>
              <input type="email" formControlName="replyTo" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" />
            </label>
          </div>

          <label class="mt-6 flex items-center gap-3 text-sm text-slate-300">
            <input type="checkbox" formControlName="enabled" />
            Utiliser cette boîte pour les envois du workspace
          </label>

          @if (actionError()) {
            <app-error-banner class="mt-4 block" [message]="actionError()!" />
          }
          @if (notice()) {
            <p class="mt-4 rounded-lg border border-emerald-500/30 bg-emerald-950/30 px-3 py-2 text-sm text-emerald-200">{{ notice() }}</p>
          }

          <div class="mt-6 flex flex-wrap items-center gap-3">
            <button type="submit" class="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500 disabled:opacity-50" [disabled]="busy() || form.invalid">
              Enregistrer
            </button>
            <button type="button" class="rounded-lg border border-slate-700 px-4 py-2 text-sm font-medium text-slate-200 hover:bg-slate-800 disabled:opacity-50" [disabled]="busy() || !canTest()" (click)="test()">
              Envoyer un e-mail de test
              <span class="text-slate-500">→ {{ auth.profile()?.email }}</span>
            </button>
            @if (s.configured) {
              <button type="button" class="ml-auto rounded-lg px-3 py-2 text-sm text-red-300 hover:bg-red-950/40 disabled:opacity-50" [disabled]="busy()" (click)="remove()">
                Supprimer la configuration
              </button>
            }
          </div>
        </form>

        <section class="space-y-3">
          <h2 class="text-lg font-semibold text-white">Journal des envois</h2>
          @if (!log().length) {
            <p class="text-sm text-slate-500">Aucun e-mail envoyé depuis ce workspace.</p>
          } @else {
            <div class="overflow-x-auto rounded-xl border border-slate-800">
              <table class="min-w-full divide-y divide-slate-800 text-sm">
                <thead class="bg-slate-900/80 text-left text-xs uppercase text-slate-500">
                  <tr>
                    <th class="px-4 py-3">Date</th>
                    <th class="px-4 py-3">Type</th>
                    <th class="px-4 py-3">Destinataire</th>
                    <th class="px-4 py-3">Objet</th>
                    <th class="px-4 py-3">Via</th>
                    <th class="px-4 py-3">Statut</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-800">
                  @for (e of log(); track e.id) {
                    <tr>
                      <td class="whitespace-nowrap px-4 py-3 text-slate-400">{{ e.createdAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                      <td class="px-4 py-3 text-slate-300">{{ kindLabel(e.kind) }}</td>
                      <td class="px-4 py-3 text-white">{{ e.toEmail }}</td>
                      <td class="max-w-xs truncate px-4 py-3 text-slate-300" [title]="e.subject">{{ e.subject }}</td>
                      <td class="px-4 py-3 text-xs text-slate-500">{{ e.senderSource === 'WORKSPACE' ? e.fromEmail : 'Éditeur' }}</td>
                      <td class="px-4 py-3">
                        @switch (e.status) {
                          @case ('SENT') { <span class="text-emerald-300">Envoyé</span> }
                          @case ('FAILED') { <span class="text-red-300" [title]="e.lastError ?? ''">Échec</span> }
                          @default { <span class="text-slate-400">En cours</span> }
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <app-paginator [meta]="page.meta()" [disabled]="logLoading()" (pageChange)="page.goTo($event); loadLog()" (pageSizeChange)="page.setSize($event); loadLog()" />
          }
        </section>
      }
    </div>
  `,
})
export class WorkspaceMailSettingsPageComponent {
  private readonly api = inject(MailApiService);
  private readonly fb = inject(FormBuilder);
  readonly workspaces = inject(WorkspaceService);
  readonly auth = inject(AuthService);

  readonly presets = SMTP_PRESETS;
  readonly settings = signal<WorkspaceMailSettings | null>(null);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly preset = signal<string>('gmail');
  readonly presetHint = computed(() => this.presets.find((p) => p.id === this.preset())?.hint ?? '');
  readonly appPasswordGuide = computed(() => this.presets.find((p) => p.id === this.preset())?.appPassword ?? null);

  readonly log = signal<EmailLogEntry[]>([]);
  readonly logLoading = signal(false);
  readonly page = new PageState(10);

  readonly form = this.fb.nonNullable.group({
    enabled: [true],
    host: ['smtp.gmail.com', [Validators.required, Validators.maxLength(255)]],
    port: [587, [Validators.required, Validators.min(1), Validators.max(65535)]],
    security: ['STARTTLS' as SmtpSecurity, Validators.required],
    username: ['', [Validators.required, Validators.maxLength(255)]],
    password: [''],
    fromEmail: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    fromName: ['', [Validators.maxLength(255)]],
    replyTo: ['', [Validators.email, Validators.maxLength(255)]],
  });

  readonly canTest = computed(() => {
    const s = this.settings();
    return !!s && (s.passwordSet || !!this.form.controls.password.value);
  });

  constructor() {
    effect(() => {
      const id = this.workspaces.selectedId();
      this.workspaces.generation();
      if (id) {
        this.load(id);
      }
    });
    // L'identifiant Gmail est l'adresse elle-même : on pré-remplit l'expéditeur.
    this.form.controls.username.valueChanges.pipe(takeUntilDestroyed()).subscribe((v) => {
      if (!this.form.controls.fromEmail.dirty && v.includes('@')) {
        this.form.controls.fromEmail.setValue(v);
      }
    });
  }

  kindLabel(kind: EmailLogEntry['kind']): string {
    return KIND_LABELS[kind] ?? kind;
  }

  /** Les mots de passe d’application (Google…) sont affichés par groupes de 4 : on tolère le copier-coller avec espaces. */
  private passwordValue(): string | null {
    const raw = this.form.controls.password.value;
    const cleaned = this.appPasswordGuide() ? raw.replace(/\s+/g, '') : raw;
    return cleaned || null;
  }

  applyPreset(id: string): void {
    const p = this.presets.find((x) => x.id === id);
    if (!p) {
      return;
    }
    this.preset.set(id);
    this.form.patchValue({ port: p.port, security: p.security, ...(p.host ? { host: p.host } : {}) });
  }

  private load(workspaceId: string): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.notice.set(null);
    this.actionError.set(null);
    this.api
      .getSettings(workspaceId)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (s) => this.apply(s),
        error: (err) => this.loadError.set(apiErrorMessage(err, 'Impossible de charger la messagerie')),
      });
    this.loadLog();
  }

  loadLog(): void {
    const id = this.workspaces.selectedId();
    if (!id) {
      return;
    }
    this.logLoading.set(true);
    this.api
      .log(id, this.page.query('createdAt,desc'))
      .pipe(finalize(() => this.logLoading.set(false)))
      .subscribe({
        next: (res) => {
          this.log.set(res.content);
          this.page.apply(res);
        },
        error: () => this.log.set([]),
      });
  }

  private apply(s: WorkspaceMailSettings): void {
    this.settings.set(s);
    if (s.configured) {
      this.form.reset({
        enabled: s.enabled,
        host: s.host ?? '',
        port: s.port ?? 587,
        security: s.security ?? 'STARTTLS',
        username: s.username ?? '',
        password: '',
        fromEmail: s.fromEmail ?? '',
        fromName: s.fromName ?? '',
        replyTo: s.replyTo ?? '',
      });
      const match = this.presets.find((p) => p.host && p.host === s.host);
      this.preset.set(match?.id ?? 'custom');
    } else {
      this.form.reset({
        enabled: true,
        host: 'smtp.gmail.com',
        port: 587,
        security: 'STARTTLS',
        username: '',
        password: '',
        fromEmail: '',
        fromName: this.workspaces.selectedWorkspace()?.name ?? '',
        replyTo: '',
      });
      this.preset.set('gmail');
    }
  }

  save(): void {
    const id = this.workspaces.selectedId();
    if (!id || this.form.invalid || this.busy()) {
      return;
    }
    const v = this.form.getRawValue();
    this.run(
      this.api.saveSettings(id, {
        enabled: v.enabled,
        host: v.host.trim(),
        port: Number(v.port),
        security: v.security,
        username: v.username.trim(),
        password: this.passwordValue(),
        fromEmail: v.fromEmail.trim(),
        fromName: v.fromName.trim() || null,
        replyTo: v.replyTo.trim() || null,
      }),
      (s) => {
        this.apply(s);
        this.notice.set('Paramètres enregistrés.');
      },
    );
  }

  test(): void {
    const id = this.workspaces.selectedId();
    if (!id || this.busy()) {
      return;
    }
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.actionError.set(null);
    this.notice.set(null);
    this.api
      .test(id, {
        host: v.host.trim(),
        port: Number(v.port),
        security: v.security,
        username: v.username.trim(),
        password: this.passwordValue(),
        fromEmail: v.fromEmail.trim(),
        fromName: v.fromName.trim() || null,
        replyTo: v.replyTo.trim() || null,
        to: null,
      })
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (res) => {
          this.notice.set(res.message + ' — vérifiez votre boîte de réception.');
          this.refreshSilently(id);
        },
        error: (err) => {
          this.actionError.set(apiErrorMessage(err, 'Le test a échoué'));
          this.refreshSilently(id);
        },
      });
  }

  remove(): void {
    const id = this.workspaces.selectedId();
    if (!id || this.busy() || !confirm('Supprimer la configuration de messagerie de ce workspace ?')) {
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    this.api
      .deleteSettings(id)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => this.load(id),
        error: (err) => this.actionError.set(apiErrorMessage(err, 'Suppression impossible')),
      });
  }

  private run<T extends WorkspaceMailSettings>(request: ReturnType<MailApiService['saveSettings']>, onDone: (s: T) => void): void {
    this.busy.set(true);
    this.actionError.set(null);
    this.notice.set(null);
    request.pipe(finalize(() => this.busy.set(false))).subscribe({
      next: (s) => onDone(s as T),
      error: (err) => this.actionError.set(apiErrorMessage(err, 'Enregistrement impossible')),
    });
  }

  private refreshSilently(workspaceId: string): void {
    this.api.getSettings(workspaceId).subscribe({ next: (s) => this.settings.set(s) });
    this.loadLog();
  }
}
