export interface Workspace {
  id: string;
  name: string;
  slug: string;
  organizationId: string;
  currencyCode: string;
  brandColor: string | null;
  logoUrl: string | null;
  active: boolean;
  legalSubtitle: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  postalCode: string | null;
  city: string | null;
  region: string | null;
  countryCode: string;
  contactEmail: string | null;
  websiteUrl: string | null;
  siren: string | null;
  vatNumber: string | null;
  iban: string | null;
  bic: string | null;
  invoicePrefix: string;
  quotationPrefix: string;
  creditNotePrefix: string;
  vatRegime: VatRegime;
  paymentTermsDays: number;
  latePenaltyRate: number;
  recoveryFeeAmount: number;
  earlyPaymentDiscount: string | null;
  paymentTermsText: string | null;
  legalFooterText: string | null;
  remindersEnabled: boolean;
  /** Motif de numérotation : {PREFIX}, {YYYY}, {YY}, {MM}, {SEQ} ou {SEQ:n}. */
  numberPattern: string;
  sequenceReset: SequenceReset;
}

export type VatRegime = 'NORMAL' | 'FRANCHISE_293B';

export type SequenceReset = 'YEARLY' | 'MONTHLY' | 'NEVER';

export const SEQUENCE_RESET_LABELS: Record<SequenceReset, string> = {
  YEARLY: 'Chaque année',
  MONTHLY: 'Chaque mois',
  NEVER: 'Jamais (compteur continu)',
};

export const DEFAULT_NUMBER_PATTERN = '{PREFIX}-{YYYY}-{SEQ:5}';

export type DocumentSequenceType = 'QUOTATION' | 'INVOICE' | 'CREDIT_NOTE';

export const SEQUENCE_TYPE_LABELS: Record<DocumentSequenceType, string> = {
  QUOTATION: 'Devis',
  INVOICE: 'Factures',
  CREDIT_NOTE: 'Avoirs',
};

/** Compteur de numérotation pour la période courante. */
export interface SequenceCounter {
  type: DocumentSequenceType;
  prefix: string;
  period: number;
  lastValue: number;
  nextNumber: string;
}

export const VAT_REGIME_LABELS: Record<VatRegime, string> = {
  NORMAL: 'Assujetti à la TVA (régime normal)',
  FRANCHISE_293B: 'Franchise en base — TVA non applicable, art. 293 B du CGI',
};

export type WorkspaceUpdatePayload = {
  name: string;
  legalSubtitle?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  postalCode?: string | null;
  city?: string | null;
  region?: string | null;
  countryCode: string;
  contactEmail?: string | null;
  websiteUrl?: string | null;
  siren?: string | null;
  vatNumber?: string | null;
  iban?: string | null;
  bic?: string | null;
  currencyCode: string;
  invoicePrefix: string;
  quotationPrefix: string;
  creditNotePrefix?: string | null;
  vatRegime?: VatRegime | null;
  paymentTermsDays?: number | null;
  latePenaltyRate?: number | null;
  recoveryFeeAmount?: number | null;
  earlyPaymentDiscount?: string | null;
  paymentTermsText?: string | null;
  legalFooterText?: string | null;
  remindersEnabled?: boolean | null;
  numberPattern?: string | null;
  sequenceReset?: SequenceReset | null;
};
