import { Injectable, inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { ModuleCode } from '../../shared/models/auth.model';

export interface ModuleInfo {
  code: ModuleCode;
  label: string;
  description: string;
}

/** Catalogue des modules optionnels (libellés affichés côté client et admin). */
export const MODULES: readonly ModuleInfo[] = [
  {
    code: 'MAINTENANCE',
    label: 'Contrats de maintenance',
    description: 'Contrats récurrents, renouvellements et suivi des échéances.',
  },
  {
    code: 'INVOICE_SCAN',
    label: 'Import de factures PDF',
    description: 'Extraction automatique (IA) des factures PDF et rapprochement.',
  },
];

export function moduleLabel(code: ModuleCode | string): string {
  return MODULES.find((m) => m.code === code)?.label ?? String(code);
}

/** Accès aux modules activés pour le tenant courant (source : `/auth/me`). */
@Injectable({ providedIn: 'root' })
export class ModuleService {
  private readonly auth = inject(AuthService);

  readonly enabled = this.auth.enabledModules;

  isEnabled(code: ModuleCode): boolean {
    return this.auth.hasModule(code);
  }
}
