import { Workspace } from '../../shared/models/workspace.model';

/** Ligne titre émetteur (nom + sous-titre légal). */
export function workspaceIssuerTitle(w: Workspace): string {
  const name = w.name?.trim() || 'Émetteur';
  const sub = w.legalSubtitle?.trim();
  return sub ? `${name} — ${sub}` : name;
}

/** Adresse postale sur une ou deux lignes (style devis NERD GROUP). */
export function workspaceIssuerAddressLines(w: Workspace): string[] {
  const lines: string[] = [];
  const street = [w.addressLine1, w.addressLine2].filter((p) => p?.trim()).join(', ');
  if (street) {
    lines.push(street);
  }
  const locality: string[] = [];
  if (w.postalCode?.trim()) {
    locality.push(w.postalCode.trim());
  }
  if (w.city?.trim()) {
    locality.push(w.city.trim());
  }
  let localityLine = locality.join(' ');
  if (w.region?.trim()) {
    localityLine = localityLine ? `${localityLine}, ${w.region.trim()}` : w.region.trim();
  }
  const country = w.countryCode === 'FR' ? 'France' : (w.countryCode?.trim() ?? '');
  if (localityLine && country) {
    lines.push(`${localityLine}, ${country}`);
  } else if (localityLine) {
    lines.push(localityLine);
  } else if (country) {
    lines.push(country);
  }
  return lines;
}

/** Une seule ligne pour icône map-pin (compat). */
export function workspaceIssuerAddressOneLine(w: Workspace): string {
  return workspaceIssuerAddressLines(w).join(', ');
}

export function workspaceIssuerSirenLabel(w: Workspace): string | null {
  const s = w.siren?.trim();
  return s ? `SIREN : ${s}` : null;
}

export function workspaceIssuerVatLabel(w: Workspace): string | null {
  const v = w.vatNumber?.trim();
  return v ? `N° TVA intracommunautaire : ${v}` : null;
}

export function workspaceIssuerLegalLine(w: Workspace): string {
  return [workspaceIssuerSirenLabel(w), workspaceIssuerVatLabel(w)].filter(Boolean).join(' • ');
}

/** IBAN groupé par 4 pour l'affichage (« FR76 1305 1492 … »). */
export function workspaceIbanFormatted(w: Pick<Workspace, 'iban'>): string | null {
  const compact = w.iban?.replace(/\s+/g, '').toUpperCase();
  if (!compact) {
    return null;
  }
  return compact.match(/.{1,4}/g)?.join(' ') ?? compact;
}

export function workspaceIssuerHasLegalProfile(w: Workspace): boolean {
  return (
    !!w.legalSubtitle?.trim() ||
    !!w.addressLine1?.trim() ||
    !!w.postalCode?.trim() ||
    !!w.city?.trim() ||
    !!w.contactEmail?.trim() ||
    !!w.siren?.trim() ||
    !!w.vatNumber?.trim()
  );
}
