export interface EtsySyncProductDraft {
  listingId: number;
  referenceCode: string;
  name: string;
  description: string;
  unitPriceHt: number;
  vatRate: number;
  alreadyInCatalog: boolean;
  existingProductId: string | null;
}

export interface EtsySyncTransactionDraft {
  transactionId: number;
  listingId: number;
  title: string;
  quantity: number;
  unitPriceHt: number;
  vatRate: number;
  lineTotalTtc: number;
}

export interface EtsySyncReceiptDraft {
  receiptId: number;
  receiptNumber: number;
  orderDate: string;
  buyerUserId: number;
  buyerName: string;
  buyerEmail: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  postalCode: string | null;
  city: string | null;
  countryCode: string | null;
  totalTtc: number;
  paid: boolean;
  alreadyImported: boolean;
  existingInvoiceId: string | null;
  transactions: EtsySyncTransactionDraft[];
}

export interface EtsySyncFetchResponse {
  sessionId: string;
  listingCount: number;
  receiptCount: number;
  products: EtsySyncProductDraft[];
  receipts: EtsySyncReceiptDraft[];
}

export interface EtsySyncProductCommit {
  listingId: number;
  importProduct: boolean;
  referenceCode: string;
  name: string;
  description: string;
  unitPriceHt: number;
  vatRate: number;
  active: boolean;
}

export interface EtsySyncReceiptCommit {
  receiptId: number;
  importReceipt: boolean;
  customerId: string | null;
  createCustomerFromBuyer: boolean;
}

export interface EtsySyncCommitRequest {
  sessionId: string;
  products: EtsySyncProductCommit[];
  receipts: EtsySyncReceiptCommit[];
}

export interface EtsySyncCommitResponse {
  productsCreated: number;
  productsUpdated: number;
  productsSkipped: number;
  receiptsImported: number;
  receiptsSkipped: number;
}
