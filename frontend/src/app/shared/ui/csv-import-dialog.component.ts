import { Component, inject, input, output, signal } from '@angular/core';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { CsvImportResult } from '../models/onboarding.model';
import { apiErrorMessage } from '../utils/api-error.util';
import { LucideIconComponent } from './lucide-icon.component';
import { Observable } from 'rxjs';

/**
 * Modale d'import CSV en deux temps : simulation (aperçu + erreurs par ligne), puis import réel.
 * L'appel HTTP est fourni par le parent (`importFn`) pour rester agnostique de l'entité (clients, produits…).
 */
@Component({
  selector: 'app-csv-import-dialog',
  standalone: true,
  imports: [LucideIconComponent],
  template: `
    <div
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 p-4"
      role="dialog"
      aria-modal="true"
      [attr.aria-label]="title()"
      (click)="onBackdrop($event)"
    >
      <div
        class="flex max-h-[90vh] w-full max-w-2xl flex-col rounded-2xl border border-slate-800 bg-slate-900 shadow-2xl"
        (click)="$event.stopPropagation()"
      >
        <div class="flex items-start justify-between gap-4 border-b border-slate-800 p-6 pb-4">
          <div>
            <h2 class="text-lg font-semibold text-white">{{ title() }}</h2>
            <p class="mt-1 text-sm text-slate-400">
              Fichier CSV (séparateur « ; », « , » ou tabulation, UTF-8), 5 000 lignes maximum. Les lignes existantes sont mises à
              jour, jamais dupliquées.
            </p>
          </div>
          <button type="button" class="rounded-lg p-1 text-slate-400 hover:bg-slate-800 hover:text-white" aria-label="Fermer" (click)="close.emit()">
            <app-lucide-icon name="x" [size]="18" />
          </button>
        </div>

        <div class="flex-1 space-y-4 overflow-y-auto p-6">
          <div class="flex flex-wrap items-center gap-3">
            <label
              class="inline-flex cursor-pointer items-center gap-2 rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-200 hover:border-slate-500"
            >
              <app-lucide-icon name="folder-open" [size]="16" />
              {{ file()?.name ?? 'Choisir un fichier…' }}
              <input type="file" accept=".csv,text/csv,text/plain" class="sr-only" (change)="onFileChange($event)" />
            </label>
            <button type="button" class="inline-flex items-center gap-1.5 text-sm text-brand-400 hover:text-brand-500" (click)="downloadTemplate()">
              <app-lucide-icon name="download" [size]="14" />
              Télécharger le modèle
            </button>
          </div>

          @if (error()) {
            <p class="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{{ error() }}</p>
          }

          @if (result(); as r) {
            <div class="rounded-xl border border-slate-800 bg-slate-950/60 p-4 text-sm">
              <p class="font-medium text-white">
                @if (r.dryRun) {
                  Simulation : {{ r.totalRows }} ligne{{ r.totalRows > 1 ? 's' : '' }} analysée{{ r.totalRows > 1 ? 's' : '' }}
                } @else {
                  Import terminé : {{ r.totalRows }} ligne{{ r.totalRows > 1 ? 's' : '' }} traitée{{ r.totalRows > 1 ? 's' : '' }}
                }
              </p>
              <div class="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-slate-300">
                <span><span class="font-semibold text-emerald-400">{{ r.created }}</span> création{{ r.created > 1 ? 's' : '' }}</span>
                <span><span class="font-semibold text-cyan-400">{{ r.updated }}</span> mise{{ r.updated > 1 ? 's' : '' }} à jour</span>
                <span><span class="font-semibold text-slate-400">{{ r.skipped }}</span> inchangée{{ r.skipped > 1 ? 's' : '' }}</span>
                <span><span class="font-semibold" [class]="r.errors.length ? 'text-rose-400' : 'text-slate-400'">{{ r.errors.length }}</span> erreur{{ r.errors.length > 1 ? 's' : '' }}</span>
              </div>

              @if (r.errors.length) {
                <ul class="mt-3 max-h-40 space-y-1 overflow-y-auto rounded-lg border border-rose-500/20 bg-rose-500/5 p-3 text-xs text-rose-200">
                  @for (e of r.errors; track e.line + e.message) {
                    <li><span class="font-mono text-rose-400">L{{ e.line }}</span> — {{ e.message }}</li>
                  }
                </ul>
                <p class="mt-2 text-xs text-slate-500">Les lignes en erreur sont ignorées ; les autres sont importées.</p>
              }

              @if (r.dryRun && r.preview.length) {
                <table class="mt-3 w-full text-xs">
                  <thead class="text-left uppercase tracking-wider text-slate-500">
                    <tr><th class="py-1 pr-3">Ligne</th><th class="py-1 pr-3">Libellé</th><th class="py-1">Action</th></tr>
                  </thead>
                  <tbody class="divide-y divide-slate-800/60 text-slate-300">
                    @for (p of r.preview; track p.line) {
                      <tr>
                        <td class="py-1 pr-3 font-mono text-slate-500">{{ p.line }}</td>
                        <td class="py-1 pr-3">{{ p.label }}</td>
                        <td class="py-1 capitalize" [class]="p.action === 'création' ? 'text-emerald-400' : p.action === 'mise à jour' ? 'text-cyan-400' : 'text-slate-500'">{{ p.action }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
                @if (r.preview.length < r.totalRows) {
                  <p class="mt-1 text-xs text-slate-500">Aperçu limité aux {{ r.preview.length }} premières lignes.</p>
                }
              }
            </div>
          }
        </div>

        <div class="flex flex-wrap items-center justify-end gap-2 border-t border-slate-800 p-4">
          <button type="button" class="rounded-lg px-4 py-2 text-sm text-slate-300 hover:bg-slate-800" (click)="close.emit()">
            {{ result() && !result()!.dryRun ? 'Fermer' : 'Annuler' }}
          </button>
          @if (!result() || result()!.dryRun) {
            <button
              type="button"
              class="rounded-lg border border-slate-700 px-4 py-2 text-sm font-medium text-slate-200 hover:bg-slate-800 disabled:opacity-50"
              [disabled]="!file() || busy()"
              (click)="run(true)"
            >
              {{ busy() && pendingDryRun() ? 'Analyse…' : 'Simuler' }}
            </button>
            <button
              type="button"
              class="rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-500 disabled:opacity-50"
              [disabled]="!file() || busy() || !result()?.dryRun"
              title="Lancez d'abord la simulation"
              (click)="run(false)"
            >
              {{ busy() && !pendingDryRun() ? 'Import…' : 'Importer' }}
            </button>
          }
        </div>
      </div>
    </div>
  `,
})
export class CsvImportDialogComponent {
  private readonly workspaces = inject(WorkspaceService);

  readonly title = input.required<string>();
  readonly importFn = input.required<(workspaceId: string, file: File, dryRun: boolean) => Observable<CsvImportResult>>();
  readonly templateFn = input.required<() => Observable<Blob>>();
  readonly templateFileName = input('modele.csv');

  readonly close = output<void>();
  /** Émis après un import réel réussi (le parent recharge sa liste). */
  readonly imported = output<CsvImportResult>();

  readonly file = signal<File | null>(null);
  readonly result = signal<CsvImportResult | null>(null);
  readonly busy = signal(false);
  readonly pendingDryRun = signal(true);
  readonly error = signal<string | null>(null);

  onFileChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files?.[0] ?? null);
    this.result.set(null);
    this.error.set(null);
  }

  run(dryRun: boolean): void {
    const file = this.file();
    const workspaceId = this.workspaces.selectedId();
    if (!file || !workspaceId || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.pendingDryRun.set(dryRun);
    this.error.set(null);
    this.importFn()(workspaceId, file, dryRun).subscribe({
      next: (r) => {
        this.result.set(r);
        this.busy.set(false);
        if (!r.dryRun) {
          this.imported.emit(r);
        }
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(apiErrorMessage(err, 'Import impossible'));
      },
    });
  }

  downloadTemplate(): void {
    this.templateFn()().subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(new Blob([blob], { type: 'text/csv;charset=utf-8' }));
        const a = document.createElement('a');
        a.href = url;
        a.download = this.templateFileName();
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => this.error.set(apiErrorMessage(err, 'Modèle indisponible')),
    });
  }

  onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget && !this.busy()) {
      this.close.emit();
    }
  }
}
