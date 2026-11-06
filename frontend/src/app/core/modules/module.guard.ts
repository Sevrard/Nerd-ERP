import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ModuleCode } from '../../shared/models/auth.model';

/** Route accessible uniquement si le module est activé pour le tenant ; sinon page « module non activé ». */
export function moduleGuard(code: ModuleCode): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    return auth.ensureProfile().pipe(
      map((me) =>
        me?.modules.includes(code) ? true : router.createUrlTree(['/module-disabled'], { queryParams: { module: code } }),
      ),
      catchError(() => of(router.createUrlTree(['/dashboard']))),
    );
  };
}

/** Route réservée aux administrateurs de la plateforme (éditeur). */
export const platformAdminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureProfile().pipe(
    map((me) => (me?.platformAdmin ? true : router.createUrlTree(['/dashboard']))),
    catchError(() => of(router.createUrlTree(['/dashboard']))),
  );
};
