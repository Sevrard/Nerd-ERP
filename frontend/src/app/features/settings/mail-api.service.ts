import { Injectable, inject } from '@angular/core';
import { ApiClient } from '../../core/api/api-client.service';
import {
  EmailLogEntry,
  MailTestPayload,
  MailTestResult,
  WorkspaceMailSettings,
  WorkspaceMailSettingsPayload,
} from '../../shared/models/mail.model';
import { PageQuery, PageResponse } from '../../shared/models/page.model';

/** Messagerie (SMTP) d'un workspace et journal des e-mails émis. */
@Injectable({ providedIn: 'root' })
export class MailApiService {
  private readonly api = inject(ApiClient);

  getSettings(workspaceId: string) {
    return this.api.get<WorkspaceMailSettings>(`/workspaces/${workspaceId}/mail/settings`);
  }

  saveSettings(workspaceId: string, payload: WorkspaceMailSettingsPayload) {
    return this.api.put<WorkspaceMailSettings>(`/workspaces/${workspaceId}/mail/settings`, payload);
  }

  deleteSettings(workspaceId: string) {
    return this.api.delete(`/workspaces/${workspaceId}/mail/settings`);
  }

  test(workspaceId: string, payload: MailTestPayload) {
    return this.api.post<MailTestResult>(`/workspaces/${workspaceId}/mail/test`, payload);
  }

  log(workspaceId: string, query: PageQuery) {
    return this.api.get<PageResponse<EmailLogEntry>>(`/workspaces/${workspaceId}/mail/log`, { ...query });
  }
}
