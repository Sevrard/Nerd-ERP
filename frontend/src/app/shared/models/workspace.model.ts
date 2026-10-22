export interface Workspace {
  id: string;
  name: string;
  slug: string;
  organizationId: string;
  currencyCode: string;
  brandColor: string | null;
  logoUrl: string | null;
  active: boolean;
}
