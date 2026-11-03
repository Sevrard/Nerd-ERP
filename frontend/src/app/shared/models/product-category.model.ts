export interface ProductCategory {
  id: string;
  workspaceId: string;
  code: string;
  label: string;
  sortOrder: number;
}

export type ProductCategoryPayload = Omit<ProductCategory, 'id'>;
