export type SmtpSecurity = 'STARTTLS' | 'SSL' | 'NONE';
export type EmailKind = 'TEST' | 'ACCOUNT' | 'QUOTATION' | 'INVOICE' | 'REMINDER';
export type EmailStatus = 'QUEUED' | 'SENT' | 'FAILED';

export interface WorkspaceMailSettings {
  workspaceId: string;
  configured: boolean;
  enabled: boolean;
  host: string | null;
  port: number | null;
  security: SmtpSecurity | null;
  username: string | null;
  passwordSet: boolean;
  fromEmail: string | null;
  fromName: string | null;
  replyTo: string | null;
  lastTestAt: string | null;
  lastTestOk: boolean | null;
  lastError: string | null;
  platformFallbackAvailable: boolean;
}

export interface WorkspaceMailSettingsPayload {
  enabled: boolean;
  host: string;
  port: number;
  security: SmtpSecurity;
  username: string;
  /** Vide = conserver le mot de passe enregistré. */
  password: string | null;
  fromEmail: string;
  fromName: string | null;
  replyTo: string | null;
}

export interface MailTestPayload extends Omit<WorkspaceMailSettingsPayload, 'enabled'> {
  to: string | null;
}

export interface MailTestResult {
  ok: boolean;
  sentTo: string;
  message: string;
}

export interface EmailLogEntry {
  id: string;
  kind: EmailKind;
  status: EmailStatus;
  fromEmail: string;
  toEmail: string;
  subject: string;
  relatedId: string | null;
  senderSource: 'WORKSPACE' | 'PLATFORM';
  attempts: number;
  createdAt: string;
  sentAt: string | null;
  lastError: string | null;
}

export interface SmtpPreset {
  id: string;
  label: string;
  host: string;
  port: number;
  security: SmtpSecurity;
  hint: string;
  /** Guide « mot de passe d’application » : étapes + lien direct vers la page du fournisseur. */
  appPassword?: { url: string; buttonLabel: string; steps: string[] };
}

/** Préréglages des fournisseurs courants (hôte / port / sécurité). */
export const SMTP_PRESETS: readonly SmtpPreset[] = [
  {
    id: 'gmail',
    label: 'Gmail / Google Workspace',
    host: 'smtp.gmail.com',
    port: 587,
    security: 'STARTTLS',
    hint: 'Google n’accepte pas le mot de passe de votre compte : il faut un « mot de passe d’application » (voir ci-dessous).',
    appPassword: {
      url: 'https://myaccount.google.com/apppasswords',
      buttonLabel: 'Créer un mot de passe d’application Google',
      steps: [
        'Connectez-vous avec la boîte à utiliser (la validation en 2 étapes doit être activée sur ce compte).',
        'Donnez un nom, par exemple « Nerd ERP », puis cliquez sur Créer.',
        'Copiez le code de 16 caractères affiché et collez-le dans le champ ci-dessus (les espaces sont ignorés).',
      ],
    },
  },
  { id: 'ovh', label: 'OVH', host: 'ssl0.ovh.net', port: 465, security: 'SSL', hint: 'Identifiant = adresse e-mail complète.' },
  { id: 'ionos', label: 'IONOS', host: 'smtp.ionos.fr', port: 587, security: 'STARTTLS', hint: 'Identifiant = adresse e-mail complète.' },
  { id: 'infomaniak', label: 'Infomaniak', host: 'mail.infomaniak.com', port: 587, security: 'STARTTLS', hint: 'Identifiant = adresse e-mail complète.' },
  { id: 'custom', label: 'Autre serveur SMTP', host: '', port: 587, security: 'STARTTLS', hint: 'Renseignez les paramètres fournis par votre hébergeur.' },
];
