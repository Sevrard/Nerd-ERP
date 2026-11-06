/** Mode de tarification d'un produit / prestation (aligné sur `BillingType` backend). */
export type BillingType = 'UNIT' | 'DAYS' | 'FIXED_PRICE' | 'RECURRING';

/** Indicateurs globaux du catalogue (workspace), indépendants de la page affichée. */
export interface ProductStats {
  activeProducts: number;
  averageDayRateHt: number;
  catalogValueHt: number;
  productsByCategory: Record<string, number>;
}

export interface Product {
  id: string;
  workspaceId: string;
  referenceCode: string;
  name: string;
  description: string | null;
  unitPriceHt: number;
  vatRate: number;
  active: boolean;
  categoryId?: string | null;
  categoryLabel?: string | null;
  billingType: BillingType;
  /** Libellé d'unité effectif renvoyé par le serveur (« jour », « forfait », « unité »…). */
  unitLabel: string;
}

export interface ProductPayload {
  workspaceId: string;
  referenceCode: string;
  name: string;
  description: string | null;
  unitPriceHt: number;
  vatRate: number;
  active: boolean;
  categoryId?: string | null;
  billingType: BillingType;
  /** Vide / null = libellé par défaut du mode. */
  unitLabel: string | null;
}
