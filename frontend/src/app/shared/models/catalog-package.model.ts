export type PackageDiscountType = 'PERCENT' | 'FIXED';

export interface CatalogPackageItem {
  productId: string;
  productReferenceCode: string;
  productName: string;
  unitPriceHt: number;
  unitLabel: string;
  quantity: number;
  lineTotalHt: number;
}

export interface CatalogPackage {
  id: string;
  workspaceId: string;
  referenceCode: string;
  title: string;
  description: string | null;
  discountType: PackageDiscountType;
  discountValue: number;
  badgeTheme: string;
  active: boolean;
  items: CatalogPackageItem[];
  grossTotalHt: number;
  discountAmountHt: number;
  netTotalHt: number;
}

export interface CatalogPackageItemPayload {
  productId: string;
  quantity: number;
}

export type CatalogPackagePayload = {
  workspaceId: string;
  referenceCode: string;
  title: string;
  description: string | null;
  discountType: PackageDiscountType;
  discountValue: number;
  badgeTheme: string;
  active: boolean;
  items: CatalogPackageItemPayload[];
};
