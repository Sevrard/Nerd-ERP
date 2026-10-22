export type MaintenancePeriodicity = 'MONTHLY' | 'QUARTERLY' | 'YEARLY';
export type MaintenanceContractStatus =
  | 'DRAFT'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'TERMINATED'
  | 'RENEWAL_DUE';

export interface MaintenanceContract {
  id: string;
  workspaceId: string;
  customerId: string;
  productId: string | null;
  title: string;
  description: string | null;
  periodicity: MaintenancePeriodicity;
  amountHt: number;
  vatRate: number;
  startDate: string;
  endDate: string | null;
  nextRenewalDate: string | null;
  status: MaintenanceContractStatus;
}

export type MaintenanceContractPayload = Omit<MaintenanceContract, 'id'>;
