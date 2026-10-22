import { Invoice } from './invoice.model';

export type CustomerMatchStatus = 'FOUND' | 'NOT_FOUND';

export interface ExtractedCustomerScan {
  customerType: string | null;
  companyName: string | null;
  firstName: string | null;
  lastName: string | null;
  email: string | null;
  phone: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  postalCode: string | null;
  city: string | null;
  countryCode: string | null;
}

export interface ExtractedLineScan {
  description: string;
  quantity: number;
  unitPriceHt: number;
  vatRate: number;
}

export interface ExtractedInvoiceScan {
  invoiceNumber: string | null;
  orderNumber: string | null;
  merchant: string | null;
  issueDate: string | null;
  customer: ExtractedCustomerScan;
  lines: ExtractedLineScan[];
  shippingAmountHt: number | null;
  shippingVatRate: number | null;
  totalHt: number | null;
  totalVat: number | null;
  totalTtc: number | null;
  currency: string | null;
}

export interface InvoiceScanDuplicateInfo {
  detected: boolean;
  existingInvoiceId: string | null;
  existingInvoiceNumber: string | null;
  externalReference: string | null;
  existingTotalTtc: number | null;
  existingAmountPaid: number | null;
  scannedAmountTtc: number | null;
}

export interface InvoiceScanPreview {
  scanId: string;
  customerMatchStatus: CustomerMatchStatus;
  matchedCustomerId: string | null;
  extraction: ExtractedInvoiceScan;
  duplicate: InvoiceScanDuplicateInfo;
}

export interface InvoiceScanPageResult {
  pageNumber: number;
  preview: InvoiceScanPreview | null;
  errorMessage: string | null;
}

export interface InvoiceScanBatchPreview {
  pageCount: number;
  pages: InvoiceScanPageResult[];
}

export type InvoiceScanImportErrorPhase = 'analyze' | 'commit';

export interface InvoiceScanImportError {
  pageNumber: number;
  phase: InvoiceScanImportErrorPhase;
  message: string;
}

export interface InvoiceScanCommitPayload {
  scanId: string;
  workspaceId: string;
  createCustomerConfirmed: boolean;
  invoiceConfirmed: boolean;
  recordDuplicateAsPayment: boolean;
  duplicatePaymentConfirmed: boolean;
  notes?: string | null;
  merchant?: string | null;
  customerId?: string | null;
  orderNumber?: string | null;
  invoiceNumber?: string | null;
  issueDate?: string | null;
  totalTtc?: number | null;
}

/** Ligne éditable dans la modale de révision post-analyse. */
export interface InvoiceScanPendingRow {
  scanId: string;
  pageNumber: number;
  merchant: string;
  orderNumber: string;
  invoiceNumber: string;
  issueDate: string;
  totalTtc: number;
  /** UUID client, ou `__create__` pour créer depuis le PDF. */
  customerSelection: string;
  duplicateDetected: boolean;
  duplicateRef: string | null;
  extractedClientLabel: string;
  saving: boolean;
  rowError: string | null;
}

export type InvoiceScanCommitResult = Invoice;

export function scanCustomerLabel(c: ExtractedCustomerScan): string {
  if (c.companyName?.trim()) {
    return c.companyName.trim();
  }
  const name = [c.firstName, c.lastName].filter(Boolean).join(' ').trim();
  return name || 'Client inconnu';
}
