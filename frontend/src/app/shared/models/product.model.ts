export interface Product {
  id: string;
  workspaceId: string;
  referenceCode: string;
  name: string;
  description: string | null;
  unitPriceHt: number;
  vatRate: number;
  active: boolean;
}

export type ProductPayload = Omit<Product, 'id'>;
