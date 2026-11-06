import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

function errorCode(err: HttpErrorResponse): string | null {
  const body = err.error as { code?: unknown } | null | undefined;
  return body && typeof body === 'object' && typeof body.code === 'string' ? body.code : null;
}

/**
 * Token JWT refusé ou absent côté API → déconnexion (Spring renvoie alors un 403 sans body JSON).
 * Les 403 métier portant un `code` (droits insuffisants, module désactivé) ne déconnectent pas ;
 * un tenant suspendu est déconnecté.
 */
export const authErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && (err.status === 401 || err.status === 403)) {
        const hasToken = !!auth.getToken();
        const isLogin = req.url.includes('/auth/login');
        if (hasToken && !isLogin) {
          const code = err.status === 403 ? errorCode(err) : null;
          if (code === 'MODULE_DISABLED') {
            void router.navigate(['/module-disabled']);
          } else if (code === null || code === 'TENANT_SUSPENDED') {
            auth.logout();
          }
        }
      }
      return throwError(() => err);
    }),
  );
};
