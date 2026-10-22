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
  totalHt: number;
  totalVat: number;
  totalTtc: number;
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
  lines: DocumentLinePayload[];
}
