import { BillingType, Product } from '../../shared/models/product.model';
import { ProductCategory } from '../../shared/models/product-category.model';

export interface CategoryColorTheme {
  name: string;
  bg: string;
  text: string;
  border: string;
  hex: string;
}

export const CATEGORY_COLOR_PALETTE: CategoryColorTheme[] = [
  { name: 'Violet', bg: 'bg-purple-500/15', text: 'text-purple-400', border: 'border-purple-500/30', hex: '#a855f7' },
  { name: 'Cyan', bg: 'bg-cyan-500/15', text: 'text-cyan-400', border: 'border-cyan-500/30', hex: '#06b6d4' },
  { name: 'Émeraude', bg: 'bg-emerald-500/15', text: 'text-emerald-400', border: 'border-emerald-500/30', hex: '#10b981' },
  { name: 'Ambre', bg: 'bg-amber-500/15', text: 'text-amber-400', border: 'border-amber-500/30', hex: '#f59e0b' },
  { name: 'Rose', bg: 'bg-rose-500/15', text: 'text-rose-400', border: 'border-rose-500/30', hex: '#f43f5e' },
  { name: 'Indigo', bg: 'bg-indigo-500/15', text: 'text-indigo-400', border: 'border-indigo-500/30', hex: '#6366f1' },
  { name: 'Bleu Sky', bg: 'bg-sky-500/15', text: 'text-sky-400', border: 'border-sky-500/30', hex: '#0ea5e9' },
  { name: 'Orange', bg: 'bg-orange-500/15', text: 'text-orange-400', border: 'border-orange-500/30', hex: '#f97316' },
];

const COLOR_STORAGE_KEY = 'nerd-erp-category-colors';

export function categoryColorByIndex(index: number): CategoryColorTheme {
  return CATEGORY_COLOR_PALETTE[((index % CATEGORY_COLOR_PALETTE.length) + CATEGORY_COLOR_PALETTE.length) % CATEGORY_COLOR_PALETTE.length];
}

export function loadCategoryColorMap(): Record<string, string> {
  try {
    const raw = localStorage.getItem(COLOR_STORAGE_KEY);
    if (!raw) {
      return {};
    }
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== 'object') {
      return {};
    }
    return parsed as Record<string, string>;
  } catch {
    return {};
  }
}

export function saveCategoryColor(categoryId: string, hex: string): void {
  const map = loadCategoryColorMap();
  map[categoryId] = hex;
  localStorage.setItem(COLOR_STORAGE_KEY, JSON.stringify(map));
}

export function resolveCategoryColor(
  category: ProductCategory | undefined,
  colorMap: Record<string, string>,
): CategoryColorTheme {
  if (!category) {
    return CATEGORY_COLOR_PALETTE[0];
  }
  const hex = colorMap[category.id];
  if (hex) {
    return CATEGORY_COLOR_PALETTE.find((c) => c.hex === hex) ?? categoryColorByIndex(category.sortOrder);
  }
  return categoryColorByIndex(category.sortOrder);
}

export interface BillingTypeOption {
  value: BillingType;
  label: string;
  defaultUnitLabel: string;
}

/** Modes de tarification proposés dans les formulaires (ordre d'affichage). */
export const BILLING_TYPE_OPTIONS: readonly BillingTypeOption[] = [
  { value: 'UNIT', label: "À l'unité", defaultUnitLabel: 'unité' },
  { value: 'DAYS', label: 'TJM / jour', defaultUnitLabel: 'jour' },
  { value: 'FIXED_PRICE', label: 'Forfait fixe', defaultUnitLabel: 'forfait' },
  { value: 'RECURRING', label: 'Récurrent', defaultUnitLabel: 'mois' },
];

export function billingTypeLabel(type: BillingType): string {
  return BILLING_TYPE_OPTIONS.find((o) => o.value === type)?.label ?? type;
}

export function defaultUnitLabel(type: BillingType): string {
  return BILLING_TYPE_OPTIONS.find((o) => o.value === type)?.defaultUnitLabel ?? 'unité';
}

/** Libellé « mode / unité » affiché dans les sélecteurs de produits (devis, packages). */
export function billingSummary(product: Pick<Product, 'billingType' | 'unitLabel'>): string {
  switch (product.billingType) {
    case 'DAYS':
      return `TJM / ${product.unitLabel}`;
    case 'RECURRING':
      return `Récurrent / ${product.unitLabel}`;
    case 'FIXED_PRICE':
      return 'Forfait';
    default:
      return product.unitLabel;
  }
}

export function productShortDescription(description: string | null): string {
  if (!description?.trim()) {
    return '';
  }
  return description
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith('Optionnel par défaut'))
    .join(' ');
}

export function formatPriceHt(value: number): string {
  return value.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

const BADGE_THEMES: Record<string, string> = {
  purple: 'bg-purple-500/15 text-purple-400 border-purple-500/30',
  emerald: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30',
  cyan: 'bg-cyan-500/15 text-cyan-400 border-cyan-500/30',
};

export function packageBadgeClasses(theme: string): string {
  return BADGE_THEMES[theme] ?? BADGE_THEMES['purple'];
}
