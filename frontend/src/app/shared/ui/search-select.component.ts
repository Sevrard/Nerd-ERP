import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  forwardRef,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Observable, Subject, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, switchMap, tap } from 'rxjs/operators';
import { LucideIconComponent } from './lucide-icon.component';

/** Nombre de résultats demandés au serveur pour une saisie. */
export const SEARCH_SELECT_LIMIT = 15;

export interface SearchSelectItem {
  id: string;
}

/**
 * Sélecteur à recherche asynchrone (combobox) utilisable comme `formControlName` : la valeur du
 * contrôle est l'identifiant de l'élément. Les résultats viennent du serveur à chaque saisie
 * (debounce 250 ms), ce qui évite de charger la liste complète dans le navigateur.
 *
 * Quand une valeur est positionnée sans que l'élément soit connu (édition), `resolve` est
 * appelé pour afficher son libellé.
 */
@Component({
  selector: 'app-search-select',
  standalone: true,
  imports: [LucideIconComponent],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SearchSelectComponent), multi: true },
  ],
  host: { class: 'relative block' },
  template: `
    <div class="relative">
      @if (selected(); as item) {
        <div
          class="flex w-full items-center gap-2 rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
          [class.opacity-60]="disabled()"
        >
          <span class="min-w-0 flex-1 truncate">
            {{ label(item) }}
            @if (detail(item); as detail) {
              <span class="ml-1 text-xs text-slate-500">{{ detail }}</span>
            }
          </span>
          @if (!disabled()) {
            <button
              type="button"
              class="shrink-0 rounded p-0.5 text-slate-500 transition hover:text-slate-200"
              [attr.aria-label]="'Retirer ' + label(item)"
              (click)="clear()"
            >
              <app-lucide-icon name="x" [size]="14" />
            </button>
          }
        </div>
      } @else {
        <app-lucide-icon
          name="search"
          [size]="14"
          class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-500"
        />
        <input
          #inputEl
          type="text"
          role="combobox"
          autocomplete="off"
          [value]="query()"
          [placeholder]="placeholder()"
          [disabled]="disabled()"
          [attr.aria-expanded]="open()"
          [attr.aria-controls]="listId"
          [attr.aria-activedescendant]="activeIndex() >= 0 ? listId + '-' + activeIndex() : null"
          class="w-full rounded-lg border border-slate-700 bg-slate-950 py-2 pl-8 pr-3 text-sm text-white placeholder-slate-500 focus:border-cyan-500 focus:outline-none"
          (input)="onInput($any($event.target).value)"
          (focus)="onFocus()"
          (blur)="onBlur()"
          (keydown)="onKeydown($event)"
        />
      }

      @if (open() && !selected()) {
        <ul
          [id]="listId"
          role="listbox"
          class="absolute left-0 right-0 top-full z-30 mt-1 max-h-64 overflow-y-auto rounded-lg border border-slate-700 bg-slate-900 py-1 shadow-2xl"
          (mousedown)="$event.preventDefault()"
        >
          @if (loading()) {
            <li class="px-3 py-2 text-xs text-slate-500">Recherche…</li>
          } @else if (!results().length) {
            <li class="px-3 py-2 text-xs text-slate-500">{{ emptyLabel() }}</li>
          } @else {
            @for (item of results(); track item.id; let i = $index) {
              <li
                [id]="listId + '-' + i"
                role="option"
                [attr.aria-selected]="i === activeIndex()"
                class="cursor-pointer px-3 py-2 text-sm transition"
                [class.bg-slate-800]="i === activeIndex()"
                [class.text-white]="i === activeIndex()"
                [class.text-slate-200]="i !== activeIndex()"
                (mouseenter)="activeIndex.set(i)"
                (click)="select(item)"
              >
                <span class="block truncate">{{ label(item) }}</span>
                @if (detail(item); as detail) {
                  <span class="block truncate text-xs text-slate-500">{{ detail }}</span>
                }
              </li>
            }
          }
          @if (extraActionLabel()) {
            <li
              role="option"
              [attr.aria-selected]="activeIndex() === results().length"
              class="cursor-pointer border-t border-slate-800 px-3 py-2 text-sm font-medium text-cyan-400 transition hover:bg-slate-800"
              [class.bg-slate-800]="activeIndex() === results().length"
              (click)="triggerExtraAction()"
            >
              {{ extraActionLabel() }}
            </li>
          }
        </ul>
      }
    </div>
  `,
})
export class SearchSelectComponent<T extends SearchSelectItem> implements ControlValueAccessor {
  private static nextId = 0;

  private readonly destroyRef = inject(DestroyRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Recherche serveur ; la chaîne peut être vide (ouverture du champ). */
  readonly search = input.required<(q: string) => Observable<T[]>>();
  /** Chargement d'un élément par identifiant, pour afficher une valeur initiale. */
  readonly resolve = input<((id: string) => Observable<T>) | null>(null);
  readonly labelFn = input.required<(item: T) => string>();
  readonly detailFn = input<((item: T) => string | null) | null>(null);
  readonly placeholder = input('Rechercher…');
  readonly emptyLabel = input('Aucun résultat');
  /** Entrée supplémentaire en bas de liste (ex. « Créer un client »). */
  readonly extraActionLabel = input<string | null>(null);
  /**
   * Change de valeur pour forcer un rechargement des résultats (ex. changement de workspace ou
   * de filtre côté parent). Le composant réinitialise alors sa sélection si elle n'est pas connue.
   */
  readonly refreshKey = input<unknown>(null);
  /**
   * Valeur contrôlée hors Reactive Forms (identifiant ou null). Laisser `undefined` quand le
   * composant est piloté par `formControlName`.
   */
  readonly value = input<string | null | undefined>(undefined);
  /** Mode « ajout » : l'élément choisi est émis puis le champ est vidé (pas de sélection persistante). */
  readonly clearOnSelect = input(false);

  readonly itemSelected = output<T | null>();
  readonly extraAction = output<void>();

  readonly listId = `search-select-${SearchSelectComponent.nextId++}`;

  readonly query = signal('');
  readonly open = signal(false);
  readonly loading = signal(false);
  readonly results = signal<T[]>([]);
  readonly activeIndex = signal(-1);
  readonly selected = signal<T | null>(null);
  readonly disabled = signal(false);

  private readonly search$ = new Subject<string>();
  private onChange: (value: string | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;
  private pendingId: string | null = null;

  readonly optionCount = computed(() => this.results().length + (this.extraActionLabel() ? 1 : 0));

  label(item: T): string {
    return this.labelFn()(item);
  }

  detail(item: T): string | null {
    const fn = this.detailFn();
    return fn ? fn(item) : null;
  }

  constructor() {
    this.search$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        tap(() => this.loading.set(true)),
        switchMap((q) => this.search()(q).pipe(catchError(() => of([] as T[])))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((items) => {
        this.results.set(items);
        this.loading.set(false);
        this.activeIndex.set(items.length ? 0 : -1);
      });

    effect(() => {
      this.refreshKey();
      untracked(() => {
        this.results.set([]);
        if (this.pendingId) {
          this.resolvePending();
        }
      });
    });

    effect(() => {
      const v = this.value();
      if (v !== undefined) {
        untracked(() => this.writeValue(v));
      }
    });
  }

  writeValue(value: string | null): void {
    const id = value || null;
    if (!id) {
      this.selected.set(null);
      this.pendingId = null;
      return;
    }
    if (this.selected()?.id === id) {
      return;
    }
    const known = this.results().find((r) => r.id === id);
    if (known) {
      this.selected.set(known);
      this.pendingId = null;
      return;
    }
    this.pendingId = id;
    this.resolvePending();
  }

  registerOnChange(fn: (value: string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
    if (isDisabled) {
      this.open.set(false);
    }
  }

  onInput(value: string): void {
    this.query.set(value);
    this.open.set(true);
    this.search$.next(value.trim());
  }

  onFocus(): void {
    if (this.disabled()) {
      return;
    }
    this.open.set(true);
    this.search$.next(this.query().trim());
    if (!this.results().length) {
      this.loading.set(true);
    }
  }

  onBlur(): void {
    // Laisse le clic sur une option (mousedown preventDefault) se terminer avant de fermer.
    setTimeout(() => {
      this.open.set(false);
      this.onTouched();
    }, 120);
  }

  onKeydown(event: KeyboardEvent): void {
    if (!this.open() && (event.key === 'ArrowDown' || event.key === 'Enter')) {
      this.onFocus();
      event.preventDefault();
      return;
    }
    switch (event.key) {
      case 'ArrowDown':
        this.activeIndex.set(Math.min(this.optionCount() - 1, this.activeIndex() + 1));
        event.preventDefault();
        break;
      case 'ArrowUp':
        this.activeIndex.set(Math.max(0, this.activeIndex() - 1));
        event.preventDefault();
        break;
      case 'Enter': {
        const idx = this.activeIndex();
        if (idx >= 0 && idx < this.results().length) {
          this.select(this.results()[idx]);
        } else if (idx === this.results().length && this.extraActionLabel()) {
          this.triggerExtraAction();
        }
        event.preventDefault();
        break;
      }
      case 'Escape':
        this.open.set(false);
        event.preventDefault();
        break;
    }
  }

  select(item: T): void {
    this.pendingId = null;
    this.query.set('');
    this.open.set(false);
    if (this.clearOnSelect()) {
      this.selected.set(null);
      this.itemSelected.emit(item);
      return;
    }
    this.selected.set(item);
    this.onChange(item.id);
    this.onTouched();
    this.itemSelected.emit(item);
  }

  clear(): void {
    if (this.disabled()) {
      return;
    }
    this.selected.set(null);
    this.pendingId = null;
    this.onChange(null);
    this.onTouched();
    this.itemSelected.emit(null);
    // Redonne le focus au champ de saisie une fois rendu.
    setTimeout(() => this.host.nativeElement.querySelector('input')?.focus());
  }

  triggerExtraAction(): void {
    this.open.set(false);
    this.extraAction.emit();
  }

  private resolvePending(): void {
    const id = this.pendingId;
    const resolve = this.resolve();
    if (!id || !resolve) {
      return;
    }
    resolve(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (item) => {
          if (this.pendingId === id) {
            this.selected.set(item);
            this.pendingId = null;
          }
        },
        error: () => {
          if (this.pendingId === id) {
            this.pendingId = null;
          }
        },
      });
  }
}
