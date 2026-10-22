export function formatMoney(value: number | null | undefined, currency = 'EUR'): string {
  const n = Number(value ?? 0);
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(n);
}

export function lineTotals(quantity: number, unitPriceHt: number, vatRate: number) {
  const ht = round4(quantity * unitPriceHt);
  const vat = round4((ht * vatRate) / 100);
  const ttc = round4(ht + vat);
  return { ht, vat, ttc };
}

function round4(n: number): number {
  return Math.round(n * 10000) / 10000;
}
