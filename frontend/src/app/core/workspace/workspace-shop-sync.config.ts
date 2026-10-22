/** Workspaces alimentés par sync boutique — pas d'import PDF factures. */
const SHOP_SYNC_LABEL_BY_SLUG: Record<string, string> = {
  'nerd-forge': 'Etsy',
};

export function workspaceShopSyncLabel(slug: string | undefined | null): string | null {
  if (!slug) {
    return null;
  }
  return SHOP_SYNC_LABEL_BY_SLUG[slug] ?? null;
}

export function workspaceAllowsInvoicePdfImport(slug: string | undefined | null): boolean {
  return workspaceShopSyncLabel(slug) == null;
}
