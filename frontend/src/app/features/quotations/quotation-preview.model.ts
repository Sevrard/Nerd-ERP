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
  ht: string;
  vat: string;
  ttc: string;
  htNum: number;
  vatNum: number;
  ttcNum: number;
}
