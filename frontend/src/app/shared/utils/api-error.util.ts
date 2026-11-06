import { HttpErrorResponse } from '@angular/common/http';

export interface ApiErrorBody {
  message?: string;
  fieldErrors?: { field: string; message: string }[];
}

/** Message utilisateur à partir d'une erreur HTTP ou d'un objet `{ error: { message } }`. */
export function apiErrorMessage(err: unknown, fallback = 'Une erreur est survenue'): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as ApiErrorBody | string | null | undefined;
    if (body && typeof body === 'object' && typeof body.message === 'string' && body.message.trim()) {
      return body.message.trim();
    }
    if (typeof body === 'string' && body.trim()) {
      return body.trim();
    }
    if (err.status === 401 || err.status === 403) {
      return 'Session expirée ou invalide — déconnectez-vous puis reconnectez-vous.';
    }
    if (err.statusText && err.statusText !== 'Unknown Error') {
      return err.statusText;
    }
    return fallback;
  }
  if (err && typeof err === 'object') {
    const withNested = err as { error?: ApiErrorBody; message?: string };
    if (withNested.error?.message?.trim()) {
      return withNested.error.message.trim();
    }
    if (typeof withNested.message === 'string' && withNested.message.trim()) {
      return withNested.message.trim();
    }
  }
  if (err instanceof Error && err.message.trim()) {
    return err.message.trim();
  }
  return fallback;
}