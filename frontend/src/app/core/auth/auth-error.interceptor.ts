import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Token JWT refusé ou absent côté API → déconnexion (Spring renvoie souvent 403 sans body JSON). */
export const authErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && (err.status === 401 || err.status === 403)) {
        const hasToken = !!auth.getToken();
        const isLogin = req.url.includes('/auth/login');
        if (hasToken && !isLogin) {
          auth.logout();
        }
      }
      return throwError(() => err);
    }),
  );
};
