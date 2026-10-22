export interface WorkspaceUiPreset {
  accentClass: string;
  dotClass: string;
  initial: string;
}

const DEFAULT: WorkspaceUiPreset = {
  accentClass: 'text-brand-500',
  dotClass: 'bg-brand-500',
  initial: 'W',
};

/** Présentation par défaut configurable — ne prétend pas être l’identité officielle. */
export const WORKSPACE_UI_BY_SLUG: Record<string, WorkspaceUiPreset> = {
  'nerd-group': { accentClass: 'text-brand-500', dotClass: 'bg-brand-500', initial: 'NG' },
  'nerd-forge': { accentClass: 'text-orange-400', dotClass: 'bg-orange-400', initial: 'NF' },
};

export function workspaceUi(slug: string, name: string): WorkspaceUiPreset {
  return (
    WORKSPACE_UI_BY_SLUG[slug] ?? {
      ...DEFAULT,
      initial: name.slice(0, 2).toUpperCase(),
    }
  );
}
