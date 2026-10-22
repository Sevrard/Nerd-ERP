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

export function etsyApiErrorMessage(err: unknown, fallback = 'Erreur Etsy'): string {
  const raw = apiErrorMessage(err, fallback);
  if (/invalid_token|401|expir/i.test(raw)) {
    return (
      'Token Etsy expiré. Dans un terminal : cd backend && node scripts/etsy-refresh-token.mjs ' +
      '(puis redémarrez le backend). Si ça échoue, relancez node scripts/etsy-oauth.mjs.'
    );
  }
  if (/Etsy : session expirée/i.test(raw)) {
    return raw;
  }
  if (raw.length > 280) {
    return raw.slice(0, 280) + '…';
  }
  return raw;
}
