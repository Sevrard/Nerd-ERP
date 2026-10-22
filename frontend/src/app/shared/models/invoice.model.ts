export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';
export type InvoiceType = 'STANDARD' | 'CREDIT_NOTE';

export interface InvoiceLine {
  id: string;
  lineOrder: number;
  description: string;
  quantity: number;
  unitPriceHt: number;
  vatRate: number;
  lineTotalHt: number;
  lineTotalVat: number;
  lineTotalTtc: number;
  productId: string | null;
  etsyListingId: number | null;
  etsyTransactionId: number | null;
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
  lines: InvoiceLine[];
}
