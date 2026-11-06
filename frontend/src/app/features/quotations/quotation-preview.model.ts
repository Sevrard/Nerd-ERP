export interface QuotationPreviewLine {
  sku: string | null;
  label: string;
  detail: string | null;
  quantity: number;
  unit: string;
  unitPriceHt: number;
  vatRate: number;
  lineTotalHt: number;
}

export interface QuotationPreviewTotals {
  /** Sous-total HT brut (avant remise), formaté. */
  subtotalHt: string;
  /** Montant de remise HT, formaté. */
  discount: string;
  ht: string;
  vat: string;
  ttc: string;
  subtotalHtNum: number;
  discountNum: number;
  htNum: number;
  vatNum: number;
  ttcNum: number;
}

export const EMPTY_PREVIEW_TOTALS: QuotationPreviewTotals = {
  subtotalHt: '0,00 €',
  discount: '0,00 €',
  ht: '0,00 €',
  vat: '0,00 €',
  ttc: '0,00 €',
  subtotalHtNum: 0,
  discountNum: 0,
  htNum: 0,
  vatNum: 0,
  ttcNum: 0,
};
