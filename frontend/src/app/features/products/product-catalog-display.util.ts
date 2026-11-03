import { Product } from '../../shared/models/product.model';
import { ProductCategory } from '../../shared/models/product-category.model';

export type BillingDisplay = {
  modeLabel: string;
  priceUnit: string;
};

export type CategoryTheme = {
  dotClass: string;
  pillClass: string;
};

const CATEGORY_THEMES: CategoryTheme[] = [
  { dotClass: 'bg-violet-400', pillClass: 'border-violet-500/35 bg-violet-500/10 text-violet-200' },
  { dotClass: 'bg-cyan-400', pillClass: 'border-cyan-500/35 bg-cyan-500/10 text-cyan-200' },
  { dotClass: 'bg-sky-400', pillClass: 'border-sky-500/35 bg-sky-500/10 text-sky-200' },
  { dotClass: 'bg-amber-400', pillClass: 'border-amber-500/35 bg-amber-500/10 text-amber-200' },
  { dotClass: 'bg-emerald-400', pillClass: 'border-emerald-500/35 bg-emerald-500/10 text-emerald-200' },
  { dotClass: 'bg-rose-400', pillClass: 'border-rose-500/35 bg-rose-500/10 text-rose-200' },
];

const DEFAULT_THEME: CategoryTheme = {
  dotClass: 'bg-slate-400',
  pillClass: 'border-slate-600/50 bg-slate-800/80 text-slate-300',
};

export function categoryTheme(categoryId: string | null | undefined, categories: ProductCategory[]): CategoryTheme {
  if (!categoryId) {
    return DEFAULT_THEME;
  }
  const idx = categories.findIndex((c) => c.id === categoryId);
  if (idx < 0) {
    return DEFAULT_THEME;
  }
  return CATEGORY_THEMES[idx % CATEGORY_THEMES.length];
}

export function parseBillingDisplay(description: string | null | undefined): BillingDisplay {
  const text = description ?? '';
  const typeMatch = text.match(/Facturation\s*:\s*([A-Z_]+)/);
  const qtyMatch = text.match(/Quantité indicative\s*:\s*[\d.,]+\s*(\S+)/);
  const unit = qtyMatch?.[1]?.toLowerCase() ?? 'unité';
  const billingType = typeMatch?.[1] ?? 'FIXED_PRICE';

  switch (billingType) {
    case 'DAYS':
      return { modeLabel: 'TJM / Jour', priceUnit: '€ / jours' };
    case 'RECURRING':
      return { modeLabel: 'Récurrent', priceUnit: unit.includes('mois') ? '€ / mois' : `€ / ${unit}` };
    case 'FIXED_PRICE':
    default:
      return { modeLabel: 'Forfait', priceUnit: unit === 'forfait' ? '€ / forfait' : '€ / forfait' };
  }
}

export function productDescriptionExcerpt(description: string | null | undefined, max = 72): string {
  if (!description?.trim()) {
    return '';
  }
  const lines = description.split('\n').map((l) => l.trim()).filter(Boolean);
  const body = lines.filter((l) => !l.startsWith('Catégorie') && !l.startsWith('Facturation') && !l.startsWith('Quantité'));
  const text = (body.at(-1) ?? lines.at(-1) ?? '').trim();
  if (text.length <= max) {
    return text;
  }
  return `${text.slice(0, max - 1)}…`;
}

export function sortProductsByCategory(
  items: Product[],
  categories: ProductCategory[],
): Product[] {
  const order = new Map(categories.map((c, i) => [c.id, c.sortOrder * 1000 + i]));
  return [...items].sort((a, b) => {
    const ao = a.categoryId ? (order.get(a.categoryId) ?? 9999) : 10000;
    const bo = b.categoryId ? (order.get(b.categoryId) ?? 9999) : 10000;
    if (ao !== bo) {
      return ao - bo;
    }
    return a.referenceCode.localeCompare(b.referenceCode, 'fr');
  });
}

export function catalogStats(items: Product[], categories: ProductCategory[]) {
  const totalProducts = items.length;
  const activeCategories = categories.length;
  const dayRates = items
    .filter((p) => parseBillingDisplay(p.description).modeLabel === 'TJM / Jour')
    .map((p) => p.unitPriceHt);
  const avgDayRate =
    dayRates.length > 0 ? Math.round(dayRates.reduce((a, b) => a + b, 0) / dayRates.length) : 0;
  const catalogSum = items.reduce((sum, p) => sum + p.unitPriceHt, 0);
  return { totalProducts, activeCategories, avgDayRate, catalogSum };
}

export function formatPriceAmount(value: number): string {
  return new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
}
