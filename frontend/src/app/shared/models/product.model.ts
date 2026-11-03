export interface Product {
  id: string;
  workspaceId: string;
  referenceCode: string;
  name: string;
  description: string | null;
  unitPriceHt: number;
  vatRate: number;
  active: boolean;
  /** Présents lorsque le backend est à jour (migration V11+). */
  categoryId?: string | null;
  categoryLabel?: string | null;
}

export type ProductPayload = Omit<Product, 'id' | 'categoryLabel'>;
