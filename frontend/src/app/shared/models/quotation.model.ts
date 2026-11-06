export type QuotationStatus =
  | 'DRAFT'
  | 'SENT'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'CONVERTED';

export interface DocumentLinePayload {
  productId?: string | null;
  description: string;
  quantity: number;
  unitPriceHt: number;
  vatRate: number;
  /** Remise de ligne en % du HT (0 = aucune). */
  discountPercent?: number | null;
}

export interface QuotationLine extends DocumentLinePayload {
  id: string;
  lineOrder: number;
  discountPercent: number;
  lineTotalHt: number;
  lineTotalVat: number;
  lineTotalTtc: number;
}

/** Ventilation de la TVA par taux (net de remise globale). */
export interface VatLine {
  rate: number;
  baseHt: number;
  vatAmount: number;
}

export interface Quotation {
  id: string;
  workspaceId: string;
  customerId: string;
  /** Libellé client résolu côté serveur (confort d'affichage des listes). */
  customerName: string | null;
  number: string | null;
  status: QuotationStatus;
  issueDate: string | null;
  validUntil: string | null;
  /** Sous-total HT brut avant remise. */
  subtotalHt: number;
  discountPercent: number;
  discountAmountHt: number;
  /** Totaux nets après remise. */
  totalHt: number;
  totalVat: number;
  totalTtc: number;
  depositPercent: number;
  depositAmountTtc: number;
  notes: string | null;
  convertedInvoiceId: string | null;
  /** Facture d'acompte émise sur ce devis (null si aucune). */
  depositInvoiceId: string | null;
  depositInvoiceNumber: string | null;
  vatBreakdown: VatLine[];
  sentAt: string | null;
  /** Lien public d'acceptation émis (envoi par e-mail). */
  publicLinkActive: boolean;
  decidedAt: string | null;
  decidedBy: string | null;
  decisionComment: string | null;
  decisionSource: QuotationDecisionSource | null;
  lines: QuotationLine[];
}

export type QuotationDecisionSource = 'MANUAL' | 'ONLINE';

export const QUOTATION_STATUS_LABELS: Record<QuotationStatus, string> = {
  DRAFT: 'Brouillon',
  SENT: 'Envoyé',
  ACCEPTED: 'Accepté',
  REJECTED: 'Refusé',
  EXPIRED: 'Expiré',
  CONVERTED: 'Facturé',
};

/** Vue publique (lien signé) d'un devis, sans identifiants internes. */
export interface PublicQuotationLine {
  description: string;
  quantity: number;
  unitPriceHt: number;
  vatRate: number;
  discountPercent: number;
  lineTotalHt: number;
}

export interface PublicQuotation {
  number: string;
  status: QuotationStatus;
  decidable: boolean;
  expired: boolean;
  issueDate: string | null;
  validUntil: string | null;
  issuerName: string;
  issuerEmail: string | null;
  customerName: string;
  currencyCode: string;
  subtotalHt: number;
  discountPercent: number;
  discountAmountHt: number;
  totalHt: number;
  totalVat: number;
  totalTtc: number;
  depositPercent: number;
  depositAmountTtc: number;
  vatBreakdown: VatLine[];
  notes: string | null;
  decidedAt: string | null;
  decidedBy: string | null;
  lines: PublicQuotationLine[];
}

export interface QuotationPayload {
  workspaceId: string;
  customerId: string;
  issueDate?: string | null;
  validUntil?: string | null;
  notes?: string | null;
  /** Remise commerciale globale en % du HT. */
  discountPercent?: number;
  /** Acompte à la commande en % du TTC. */
  depositPercent?: number;
  lines: DocumentLinePayload[];
}
