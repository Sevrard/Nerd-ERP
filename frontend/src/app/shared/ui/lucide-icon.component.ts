import { Component, computed, input } from '@angular/core';
import type { IconNode } from 'lucide';
import {
  Archive,
  Check,
  ChevronRight,
  Download,
  ExternalLink,
  Eye,
  FileText,
  FolderOpen,
  HandCoins,
  Loader2,
  Package,
  Pencil,
  Plus,
  EyeOff,
  RefreshCw,
  Settings,
  ShoppingCart,
  Tag,
  Trash2,
  TriangleAlert,
  UserPlus,
  Users,
  Wallet,
  X,
} from 'lucide';

export const LUCIDE_ICON_MAP = {
  eye: Eye,
  'eye-off': EyeOff,
  settings: Settings,
  pencil: Pencil,
  trash: Trash2,
  chevron: ChevronRight,
  download: Download,
  'external-link': ExternalLink,
  check: Check,
  x: X,
  archive: Archive,
  'folder-open': FolderOpen,
  package: Package,
  plus: Plus,
  loader: Loader2,
  'file-text': FileText,
  wallet: Wallet,
  'hand-coins': HandCoins,
  'triangle-alert': TriangleAlert,
  users: Users,
  'user-plus': UserPlus,
  'shopping-cart': ShoppingCart,
  tag: Tag,
  'refresh-cw': RefreshCw,
} as const;

export type LucideIconName = keyof typeof LUCIDE_ICON_MAP;

@Component({
  selector: 'app-lucide-icon',
  standalone: true,
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      class="shrink-0"
      [class.animate-spin]="spin()"
    >
      @for (node of nodes(); track $index) {
        @switch (node[0]) {
          @case ('path') {
            <path [attr.d]="attr(node, 'd')" />
          }
          @case ('circle') {
            <circle [attr.cx]="attr(node, 'cx')" [attr.cy]="attr(node, 'cy')" [attr.r]="attr(node, 'r')" />
          }
          @case ('line') {
            <line
              [attr.x1]="attr(node, 'x1')"
              [attr.x2]="attr(node, 'x2')"
              [attr.y1]="attr(node, 'y1')"
              [attr.y2]="attr(node, 'y2')"
            />
          }
          @case ('polyline') {
            <polyline [attr.points]="attr(node, 'points')" />
          }
          @case ('rect') {
            <rect
              [attr.x]="attr(node, 'x')"
              [attr.y]="attr(node, 'y')"
              [attr.width]="attr(node, 'width')"
              [attr.height]="attr(node, 'height')"
              [attr.rx]="attr(node, 'rx')"
              [attr.ry]="attr(node, 'ry')"
            />
          }
        }
      }
    </svg>
  `,
})
export class LucideIconComponent {
  readonly name = input.required<LucideIconName>();
  readonly size = input(16);
  readonly spin = input(false);

  readonly nodes = computed((): IconNode => LUCIDE_ICON_MAP[this.name()] ?? Eye);

  attr(node: IconNode[number], key: string): string | undefined {
    const attrs = node[1] as Record<string, string | undefined>;
    return attrs[key];
  }
}
