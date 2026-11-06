import { AbstractControl, ValidationErrors } from '@angular/forms';

/** Validateur de groupe : `password` et `confirm` doivent être identiques. */
export function passwordMatch(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const confirm = group.get('confirm')?.value;
  return password && confirm && password !== confirm ? { passwordMismatch: true } : null;
}
