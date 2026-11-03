export type SortDirection = 'asc' | 'desc';

export function nextSortDirection(currentKey: string | null, key: string, currentDir: SortDirection): SortDirection {
  if (currentKey === key) {
    return currentDir === 'asc' ? 'desc' : 'asc';
  }
  return 'asc';
}

export function sortMultiplier(dir: SortDirection): number {
  return dir === 'asc' ? 1 : -1;
}

export function sortIndicator(activeKey: string | null, key: string, dir: SortDirection): string {
  if (activeKey !== key) {
    return '';
  }
  return dir === 'asc' ? ' ▲' : ' ▼';
}
