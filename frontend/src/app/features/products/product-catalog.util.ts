import { ProductCategory } from '../../shared/models/product-category.model';

export type BillingType = 'DAYS' | 'FIXED_PRICE' | 'RECURRING' | 'UNKNOWN';

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

export type EditableBillingType = 'DAYS' | 'FIXED_PRICE' | 'RECURRING';

export function parseIndicativeQuantity(description: string | null): { quantity: number; unitLabel: string } {
  const qtyMatch = description?.match(/Quantité indicative :\s*([\d.,]+)\s*(\S+)/);
  const quantity = qtyMatch ? parseFloat(qtyMatch[1].replace(',', '.')) || 1 : 1;
  const unitLabel = qtyMatch?.[2] ?? 'unité';
  return { quantity, unitLabel };
}

/** Met à jour les lignes Facturation / Quantité indicative en conservant le corps texte. */
export function applyBillingTypeToDescription(
  description: string | null,
  billingType: EditableBillingType,
): string {
  const { quantity } = parseIndicativeQuantity(description);
  const unitLabel =
    billingType === 'DAYS' ? 'jours' : billingType === 'FIXED_PRICE' ? 'forfait' : 'mois';
  const factLine = `Facturation : ${billingType}`;
  const qtyLine = `Quantité indicative : ${quantity} ${unitLabel}`;

  if (!description?.trim()) {
    return [factLine, qtyLine].join('\n');
  }

  const lines = description.split('\n');
  let hasFact = false;
  let hasQty = false;
  const updated = lines.map((line) => {
    if (line.startsWith('Facturation :')) {
      hasFact = true;
      return factLine;
    }
    if (line.startsWith('Quantité indicative :')) {
      hasQty = true;
      return qtyLine;
    }
    return line;
  });

  if (!hasFact || !hasQty) {
    const insertAt = updated.findIndex((l) => l.trim() === '');
    const prefix = [factLine, qtyLine];
    if (insertAt >= 0) {
      updated.splice(insertAt, 0, ...prefix);
    } else {
      updated.unshift(...prefix, '');
    }
  }

  return updated.join('\n');
}

export function editableBillingFromDescription(description: string | null): EditableBillingType {
  const { type } = parseBillingFromDescription(description);
  if (type === 'DAYS' || type === 'FIXED_PRICE' || type === 'RECURRING') {
    return type;
  }
  return 'FIXED_PRICE';
}

export function parseBillingFromDescription(description: string | null): { type: BillingType; unitLabel: string } {
  if (!description) {
    return { type: 'UNKNOWN', unitLabel: 'unité' };
  }
  const billMatch = description.match(/Facturation :\s*(\w+)/);
  const qtyMatch = description.match(/Quantité indicative :\s*[\d.,]+\s*(\S+)/);
  const raw = billMatch?.[1] ?? '';
  const type: BillingType =
    raw === 'DAYS' || raw === 'FIXED_PRICE' || raw === 'RECURRING' ? raw : 'UNKNOWN';
  let unitLabel = qtyMatch?.[1] ?? 'unité';
  if (type === 'FIXED_PRICE') {
    unitLabel = 'forfait';
  }
  if (type === 'RECURRING') {
    unitLabel = qtyMatch?.[1] ?? 'mois';
  }
  return { type, unitLabel };
}

export function productShortDescription(description: string | null): string {
  if (!description?.trim()) {
    return '';
  }
  const lines = description
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean);
  const body = lines.filter(
    (l) =>
      !l.startsWith('Catégorie :') &&
      !l.startsWith('Facturation :') &&
      !l.startsWith('Quantité indicative :') &&
      !l.startsWith('Optionnel par défaut'),
  );
  return body.join(' ');
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
