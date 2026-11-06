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
}

export interface QuotationLine extends DocumentLinePayload {
  id: string;
  lineOrder: number;
  lineTotalHt: number;
  lineTotalVat: number;
  lineTotalTtc: number;
}

export interface Quotation {
  id: string;
  workspaceId: string;
  customerId: string;
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
  lines: QuotationLine[];
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
