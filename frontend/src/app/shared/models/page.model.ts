import { SortDirection } from '../utils/table-sort.util';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Paramètres de pagination Spring Data (`page`, `size`, `sort=prop,dir`). */
export interface PageQuery {
  page?: number;
  size?: number;
  sort?: string;
}

export const DEFAULT_PAGE_SIZE = 20;
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

export function emptyPage<T>(size = DEFAULT_PAGE_SIZE): PageResponse<T> {
  return { content: [], page: 0, size, totalElements: 0, totalPages: 0 };
}

/** Construit le paramètre `sort` Spring (`createdAt,desc`) ; `undefined` si aucune clé. */
export function sortParam(key: string | null | undefined, dir: SortDirection): string | undefined {
  return key ? `${key},${dir}` : undefined;
}
