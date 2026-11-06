export function formatMoney(value: number | null | undefined, currency = 'EUR'): string {
  const n = Number(value ?? 0);
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(n);
}

/** Totaux d'une ligne : HT = qté × PU × (1 − remise ligne / 100). */
export function lineTotals(quantity: number, unitPriceHt: number, vatRate: number, discountPercent = 0) {
  const gross = round4(quantity * unitPriceHt);
  const discount = discountPercent > 0 ? round4((gross * discountPercent) / 100) : 0;
  const ht = round4(gross - discount);
  const vat = round4((ht * vatRate) / 100);
  const ttc = round4(ht + vat);
  return { ht, vat, ttc };
}

export interface VatBreakdownLine {
  rate: number;
  baseHt: number;
  vatAmount: number;
}

/**
 * Ventilation TVA par taux (même règle que le backend : la remise globale est appliquée
 * proportionnellement à chaque ligne, puis les bases sont regroupées par taux croissant).
 */
export function vatBreakdown(
  lines: { vatRate: number; ht: number }[],
  globalDiscountPercent = 0,
): VatBreakdownLine[] {
  const factor = globalDiscountPercent > 0 ? 1 - globalDiscountPercent / 100 : 1;
  const byRate = new Map<number, number>();
  for (const l of lines) {
    const base = round4(l.ht * factor);
    byRate.set(l.vatRate, round4((byRate.get(l.vatRate) ?? 0) + base));
  }
  return [...byRate.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([rate, baseHt]) => ({ rate, baseHt, vatAmount: round4((baseHt * rate) / 100) }));
}

/** Rendu d'un taux (« 5,5 % », « 20 % »). */
export function formatRate(rate: number): string {
  return `${new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 }).format(rate)} %`;
}

function round4(n: number): number {
  return Math.round(n * 10000) / 10000;
}
