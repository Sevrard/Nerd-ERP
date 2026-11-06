/** Résultat d'un import CSV (clients ou produits), en simulation (`dryRun`) ou réel. */
export interface CsvImportError {
  line: number;
  message: string;
}

export interface CsvImportPreview {
  line: number;
  label: string;
  /** Libellé lisible produit par le serveur : « création », « mise à jour », « inchangé ». */
  action: string;
}

export interface CsvImportResult {
  dryRun: boolean;
  totalRows: number;
  created: number;
  updated: number;
  skipped: number;
  errors: CsvImportError[];
  preview: CsvImportPreview[];
}

/** Habillage de l'écran de connexion propre au compte client (`/auth/public/branding`). */
export interface LoginBranding {
  tenantSlug: string | null;
  tenantName: string | null;
  brandColor: string | null;
  logoUrl: string | null;
  tagline: string | null;
}

/** État d'acceptation des CGU par l'utilisateur connecté. */
export interface TermsStatus {
  currentVersion: string;
  acceptedVersion: string | null;
  acceptedAt: string | null;
  accepted: boolean;
}

export type OnboardingStepCode =
  | 'TERMS'
  | 'LEGAL_PROFILE'
  | 'MAIL'
  | 'CUSTOMER'
  | 'PRODUCT'
  | 'QUOTATION'
  | 'INVOICE';

export interface OnboardingStep {
  code: OnboardingStepCode;
  label: string;
  description: string;
  done: boolean;
  /** Route Angular vers l'écran concerné (absente si l'action se fait ailleurs, ex. barre latérale). */
  route: string | null;
}

export interface OnboardingChecklist {
  workspaceId: string;
  done: number;
  total: number;
  steps: OnboardingStep[];
}
