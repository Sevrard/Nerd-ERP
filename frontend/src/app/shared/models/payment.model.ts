export type PaymentMethod = 'TRANSFER' | 'CARD' | 'CHECK' | 'CASH' | 'OTHER';

export interface PaymentAllocationPayload {
  invoiceId: string;
  amount: number;
}

export interface PaymentPayload {
  workspaceId: string;
  customerId: string;
  amount: number;
  paymentDate: string;
  method?: PaymentMethod;
  reference?: string | null;
  notes?: string | null;
  allocations: PaymentAllocationPayload[];
}

export interface Payment {
  id: string;
  workspaceId: string;
  customerId: string;
  amount: number;
  paymentDate: string;
  method: PaymentMethod;
  reference: string | null;
  notes: string | null;
  allocations: { invoiceId: string; amount: number }[];
}
