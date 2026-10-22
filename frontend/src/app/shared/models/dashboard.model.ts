export interface DashboardSummary {
  invoicedRevenue: number;
  collectedAmount: number;
  outstandingAmount: number;
  overdueInvoices: number;
  activeProspects: number;
  activeClients: number;
  pendingQuotations: number;
  maintenanceRenewalsDue: number;
  invoiceCountInPeriod: number;
  unitsSold: number;
}

export interface MonthlyRevenuePoint {
  month: string;
  label: string;
  invoicedRevenue: number;
  collectedAmount: number;
}

export interface RevenueTrendResponse {
  points: MonthlyRevenuePoint[];
}
