export interface Workspace {
  id: string;
  name: string;
  slug: string;
  organizationId: string;
  currencyCode: string;
  brandColor: string | null;
  logoUrl: string | null;
  active: boolean;
  legalSubtitle: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  postalCode: string | null;
  city: string | null;
  region: string | null;
  countryCode: string;
  contactEmail: string | null;
  websiteUrl: string | null;
  siren: string | null;
  vatNumber: string | null;
  iban: string | null;
  bic: string | null;
  invoicePrefix: string;
  quotationPrefix: string;
}

export type WorkspaceUpdatePayload = {
  name: string;
  legalSubtitle?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  postalCode?: string | null;
  city?: string | null;
  region?: string | null;
  countryCode: string;
  contactEmail?: string | null;
  websiteUrl?: string | null;
  siren?: string | null;
  vatNumber?: string | null;
  iban?: string | null;
  bic?: string | null;
  currencyCode: string;
  invoicePrefix: string;
  quotationPrefix: string;
};
