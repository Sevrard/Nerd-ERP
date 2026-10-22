export interface ProductSalesStat {
  productId: string | null;
  productName: string;
  productReferenceCode: string;
  quantitySold: number;
  revenueHt: number;
  revenueTtc: number;
  orderCount: number;
  lineCount: number;
}
