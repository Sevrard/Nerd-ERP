import type { DocumentLinePayload, VatLine } from './quotation.model';

export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';
export type InvoiceType = 'STANDARD' | 'DEPOSIT' | 'CREDIT_NOTE';

export const INVOICE_TYPE_LABELS: Record<InvoiceType, string> = {
  STANDARD: 'Facture',
  DEPOSIT: "Facture d'acompte",
  CREDIT_NOTE: 'Avoir',
};

export interface InvoiceLine {
  id: string;
  lineOrder: number;
  description: string;
  quantity: number;
  unitPriceHt: number;
  vatRate: number;
  discountPercent: number;
  lineTotalHt: number;
  lineTotalVat: number;
  lineTotalTtc: number;
  productId: string | null;
}

export function invoiceCustomerDisplayName(invoice: Pick<Invoice, 'snapshotCustomer'>): string {
  const snap = invoice.snapshotCustomer;
  if (!snap) {
    return '—';
  }
  const display = snap['displayName'];
  if (typeof display === 'string' && display.trim()) {
    return display.trim();
  }
  const company = snap['companyName'];
  if (typeof company === 'string' && company.trim()) {
    return company.trim();
  }
  const parts = [snap['firstName'], snap['lastName']].filter((v) => typeof v === 'string' && v.trim());
  return parts.join(' ').trim() || '—';
}

export interface Invoice {
  id: string;
  workspaceId: string;
  customerId: string;
  sourceQuotationId: string | null;
  invoiceType: InvoiceType;
  number: string;
  status: InvoiceStatus;
  issueDate: string;
  dueDate: string;
  /** Sous-total HT brut avant remise. */
  subtotalHt: number;
  discountPercent: number;
  discountAmountHt: number;
  totalHt: number;
  totalVat: number;
  totalTtc: number;
  amountPaid: number;
  snapshotCustomer: Record<string, unknown>;
  notes: string | null;
  externalReference: string | null;
  merchant: string | null;
  orderNumber: string | null;
  issuedAt: string | null;
  /** Avoir : facture d'origine. */
  creditedInvoiceId: string | null;
  creditedInvoiceNumber: string | null;
  creditReason: string | null;
  /** Facture : avoirs émis dessus. */
  creditNotes: LinkedDocument[];
  /** Facture de solde : acompte déduit. */
  depositInvoice: LinkedDocument | null;
  /** Facture d'acompte : facture de solde émise ensuite. */
  balanceInvoice: LinkedDocument | null;
  vatBreakdown: VatLine[];
  sentAt: string | null;
  reminderCount: number;
  lastReminderAt: string | null;
  lines: InvoiceLine[];
}

export interface LinkedDocument {
  id: string;
  number: string;
  totalTtc: number;
}

export interface CreditNotePayload {
  reason: string;
  /** Vide = avoir total. */
  lines?: DocumentLinePayload[] | null;
}

/** Envoi d'un document par e-mail (destinataire vide = e-mail du client). */
export interface DocumentEmailPayload {
  to?: string | null;
  cc?: string | null;
  message?: string | null;
}

export interface DocumentEmailResult {
  to: string;
  sentAt: string;
}

export const INVOICE_STATUS_LABELS: Record<InvoiceStatus, string> = {
  DRAFT: 'Brouillon',
  ISSUED: 'Émise',
  PARTIALLY_PAID: 'Partiellement payée',
  PAID: 'Payée',
  CANCELLED: 'Annulée',
};
