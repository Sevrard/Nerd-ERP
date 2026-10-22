export type CustomerType = 'COMPANY' | 'INDIVIDUAL';
export type CustomerStatus = 'PROSPECT' | 'CLIENT' | 'INACTIVE';

export interface Customer {
  id: string;
  workspaceId: string;
  customerType: CustomerType;
  companyName: string | null;
  firstName: string | null;
  lastName: string | null;
  email: string | null;
  phone: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  postalCode: string | null;
  city: string | null;
  countryCode: string;
  status: CustomerStatus;
  notes: string | null;
  nextFollowUpAt: string | null;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
}

export type CustomerPayload = Omit<
  Customer,
  'id' | 'archived' | 'createdAt' | 'updatedAt'
> & { workspaceId: string };

export interface FollowUp {
  id: string;
  workspaceId: string;
  customerId: string;
  scheduledAt: string;
  actionType: string;
  notes: string | null;
  completed: boolean;
  completedAt: string | null;
}

export interface FollowUpPayload {
  workspaceId: string;
  scheduledAt: string;
  actionType: string;
  notes?: string | null;
}

export interface CustomerHistory {
  followUps: FollowUp[];
  quotations: { id: string; number: string | null; status: string; totalTtc: number }[];
  invoices: { id: string; number: string; status: string; totalTtc: number }[];
  payments: { id: string; amount: number; paymentDate: string }[];
}

export function customerDisplayName(c: Pick<Customer, 'customerType' | 'companyName' | 'firstName' | 'lastName'>): string {
  if (c.customerType === 'COMPANY') {
    return c.companyName?.trim() || '—';
  }
  const name = [c.firstName, c.lastName].filter(Boolean).join(' ').trim();
  return name || '—';
}
