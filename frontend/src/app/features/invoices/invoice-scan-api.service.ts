import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  InvoiceScanBatchPreview,
  InvoiceScanCommitPayload,
  InvoiceScanCommitResult,
} from '../../shared/models/invoice-scan.model';

@Injectable({ providedIn: 'root' })
export class InvoiceScanApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  analyze(workspaceId: string, file: File, merchantId?: string | null): Observable<InvoiceScanBatchPreview> {
    const form = new FormData();
    form.append('file', file, file.name);
    let url = `${this.base}/invoice-scan/analyze?workspaceId=${encodeURIComponent(workspaceId)}`;
    if (merchantId) {
      url += `&merchantId=${encodeURIComponent(merchantId)}`;
    }
    return this.http.post<InvoiceScanBatchPreview>(url, form);
  }

  commit(payload: InvoiceScanCommitPayload): Observable<InvoiceScanCommitResult> {
    return this.http.post<InvoiceScanCommitResult>(`${this.base}/invoice-scan/commit`, payload);
  }
}
