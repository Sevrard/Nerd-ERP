import { Component, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DocumentEmailPayload } from '../models/invoice.model';
import { LucideIconComponent } from './lucide-icon.component';

/**
 * Modale d'envoi d'un document (devis, facture, relance) par e-mail : destinataire pré-rempli
 * depuis la fiche client, copie optionnelle, message personnel. L'envoi lui-même est délégué au parent.
 */
@Component({
  selector: 'app-document-email-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, LucideIconComponent],
  template: `
    <div
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 p-4"
      role="dialog"
      aria-modal="true"
      [attr.aria-label]="title()"
      (click)="onBackdrop($event)"
    >
      <form
        class="w-full max-w-lg space-y-4 rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-2xl"
        [formGroup]="form"
        (ngSubmit)="submit()"
        (click)="$event.stopPropagation()"
      >
        <div class="flex items-start justify-between gap-4">
          <div>
            <h2 class="text-lg font-semibold text-white">{{ title() }}</h2>
            @if (subtitle()) {
              <p class="mt-1 text-sm text-slate-400">{{ subtitle() }}</p>
            }
          </div>
          <button
            type="button"
            class="rounded-lg p-1 text-slate-400 hover:bg-slate-800 hover:text-white"
            aria-label="Fermer"
            (click)="close.emit()"
          >
            <app-lucide-icon name="x" [size]="18" />
          </button>
        </div>

        @if (error()) {
          <p class="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{{ error() }}</p>
        }

        <div>
          <label class="mb-1.5 block text-sm text-slate-400" for="doc-email-to">Destinataire</label>
          <input
            id="doc-email-to"
            type="email"
            formControlName="to"
            placeholder="client@exemple.fr"
            class="w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-white focus:border-cyan-500/50 focus:outline-none"
          />
          @if (form.controls.to.invalid && form.controls.to.touched) {
            <p class="mt-1 text-xs text-rose-400">Adresse e-mail requise.</p>
          }
        </div>
        <div>
          <label class="mb-1.5 block text-sm text-slate-400" for="doc-email-cc">Copie (optionnel)</label>
          <input
            id="doc-email-cc"
            type="email"
            formControlName="cc"
            class="w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-white focus:border-cyan-500/50 focus:outline-none"
          />
        </div>
        <div>
          <label class="mb-1.5 block text-sm text-slate-400" for="doc-email-message">Message personnel (optionnel)</label>
          <textarea
            id="doc-email-message"
            formControlName="message"
            rows="4"
            [placeholder]="messagePlaceholder()"
            class="w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-white focus:border-cyan-500/50 focus:outline-none"
          ></textarea>
          <p class="mt-1 text-xs text-slate-500">{{ hint() }}</p>
        </div>

        <div class="flex justify-end gap-2 pt-2">
          <button
            type="button"
            class="rounded-lg border border-slate-700 px-4 py-2 text-sm text-slate-200 hover:bg-slate-800"
            (click)="close.emit()"
          >
            Annuler
          </button>
          <button
            type="submit"
            class="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500 disabled:opacity-50"
            [disabled]="form.invalid || busy()"
          >
            @if (busy()) {
              <app-lucide-icon name="loader" [size]="16" [spin]="true" />
            } @else {
              <app-lucide-icon name="send" [size]="16" />
            }
            {{ submitLabel() }}
          </button>
        </div>
      </form>
    </div>
  `,
})
export class DocumentEmailDialogComponent {
  private readonly fb = inject(FormBuilder);

  readonly title = input.required<string>();
  readonly subtitle = input<string | null>(null);
  readonly defaultTo = input<string | null>(null);
  readonly submitLabel = input('Envoyer');
  readonly hint = input('Le PDF est joint automatiquement.');
  readonly messagePlaceholder = input('Bonjour, veuillez trouver ci-joint…');
  readonly busy = input(false);
  readonly error = input<string | null>(null);

  readonly send = output<DocumentEmailPayload>();
  readonly close = output<void>();

  readonly form = this.fb.nonNullable.group({
    to: ['', [Validators.required, Validators.email]],
    cc: ['', [Validators.email]],
    message: [''],
  });

  private readonly initialised = signal(false);

  constructor() {
    effect(() => {
      const to = this.defaultTo();
      if (!this.initialised() && to) {
        this.form.patchValue({ to });
        this.initialised.set(true);
      }
    });
  }

  submit(): void {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.send.emit({
      to: v.to.trim(),
      cc: v.cc.trim() || null,
      message: v.message.trim() || null,
    });
  }

  onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.close.emit();
    }
  }
}
