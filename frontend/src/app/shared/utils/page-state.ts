import { signal } from '@angular/core';
import { DEFAULT_PAGE_SIZE, emptyPage, PageQuery, PageResponse } from '../models/page.model';

export type PageMeta = Pick<PageResponse<unknown>, 'page' | 'size' | 'totalElements' | 'totalPages'>;

/**
 * État de pagination serveur d'une page liste : index, taille et métadonnées de la dernière réponse.
 * Les composants appellent `query()` pour construire la requête et `apply()` sur la réponse.
 */
export class PageState {
  readonly index = signal(0);
  readonly size = signal(DEFAULT_PAGE_SIZE);
  readonly meta = signal<PageMeta>(emptyPage());

  constructor(size = DEFAULT_PAGE_SIZE) {
    this.size.set(size);
    this.meta.set(emptyPage(size));
  }

  query(sort?: string): PageQuery {
    return { page: this.index(), size: this.size(), sort };
  }

  apply(page: PageResponse<unknown>): void {
    this.index.set(page.page);
    this.meta.set({
      page: page.page,
      size: page.size,
      totalElements: page.totalElements,
      totalPages: page.totalPages,
    });
  }

  /** Revient en première page (changement de filtre, de tri, de workspace…). */
  reset(): void {
    this.index.set(0);
  }

  goTo(page: number): void {
    this.index.set(Math.max(0, page));
  }

  setSize(size: number): void {
    this.size.set(size);
    this.index.set(0);
  }

  /** Après une suppression : si la page courante devient vide, recule d'une page. */
  adjustAfterRemoval(): void {
    const meta = this.meta();
    if (meta.page > 0 && meta.totalElements - 1 <= meta.page * meta.size) {
      this.index.set(meta.page - 1);
    }
  }
}
