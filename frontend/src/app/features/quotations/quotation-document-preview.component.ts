import { DatePipe } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { Customer, customerDisplayName } from '../../shared/models/customer.model';
import {
  workspaceIbanFormatted,
  workspaceIssuerAddressLines,
  workspaceIssuerAddressOneLine,
  workspaceIssuerHasLegalProfile,
  workspaceIssuerLegalLine,
  workspaceIssuerSirenLabel,
  workspaceIssuerTitle,
  workspaceIssuerVatLabel,
} from '../../core/workspace/workspace-issuer.util';
import { Workspace } from '../../shared/models/workspace.model';
import { formatPriceHt } from '../products/product-catalog.util';
import { formatRate } from '../../shared/utils/money.util';
import { EMPTY_PREVIEW_TOTALS, QuotationPreviewLine, QuotationPreviewTotals } from './quotation-preview.model';

export type QuotationPreviewViewMode = 'screen' | 'print';

@Component({
  selector: 'app-quotation-document-preview',
  standalone: true,
  imports: [DatePipe, LucideIconComponent],
  templateUrl: './quotation-document-preview.component.html',
  host: {
    class: 'block',
  },
  styles: [
    `
      .quotation-preview-light .signature-pad {
        border-color: rgb(100 116 139);
        background-color: rgb(248 250 252);
      }
      .quotation-preview-light .signature-pad canvas {
        background-color: #fff;
      }
      .quotation-preview-light .signature-placeholder {
        color: rgb(71 85 105);
        opacity: 0.75;
      }
    `,
  ],
})
export class QuotationDocumentPreviewComponent implements AfterViewInit {
  readonly workspace = input<Workspace | null>(null);
  readonly customer = input<Customer | null>(null);
  readonly documentNumber = input('Brouillon');
  readonly statusLabel = input("En attente d'approbation");
  readonly issueDate = input<string | null>(null);
  readonly validUntil = input<string | null>(null);
  readonly subject = input('Prestations et services');
  readonly executiveSummary = input<string | null>(null);
  readonly lines = input<QuotationPreviewLine[]>([]);
  readonly totals = input<QuotationPreviewTotals>(EMPTY_PREVIEW_TOTALS);
  readonly viewMode = input<QuotationPreviewViewMode>('screen');
  readonly embedded = input(false);
  /** Remise commerciale globale (% du HT) — purement informatif ici, les totaux sont déjà nets. */
  readonly discountPercent = input(0);
  /** Acompte à la commande (% du TTC). */
  readonly depositPercent = input(0);

  readonly signatureDate = signal('');
  readonly hasSignature = signal(false);

  private readonly canvasRef = viewChild<ElementRef<HTMLCanvasElement>>('signatureCanvas');
  private signing = false;

  readonly hasDiscount = () => this.totals().discountNum > 0;
  readonly hasDeposit = () => this.depositPercent() > 0;
  readonly depositAmount = () => Math.round(this.totals().ttcNum * this.depositPercent()) / 100;
  readonly balanceDue = () => this.totals().ttcNum - this.depositAmount();

  ibanFormatted(w: Workspace): string | null {
    return workspaceIbanFormatted(w);
  }

  ngAfterViewInit(): void {
    this.resizeCanvas();
  }

  issuerTitle(w: Workspace): string {
    return workspaceIssuerTitle(w);
  }

  issuerAddressLines(w: Workspace): string[] {
    return workspaceIssuerAddressLines(w);
  }

  issuerAddressOneLine(w: Workspace): string {
    return workspaceIssuerAddressOneLine(w);
  }

  issuerLegalLine(w: Workspace): string {
    return workspaceIssuerLegalLine(w);
  }

  issuerSirenLabel(w: Workspace): string | null {
    return workspaceIssuerSirenLabel(w);
  }

  issuerVatLabel(w: Workspace): string | null {
    return workspaceIssuerVatLabel(w);
  }

  issuerProfileComplete(w: Workspace): boolean {
    return workspaceIssuerHasLegalProfile(w);
  }

  customerContact(c: Customer): string {
    if (c.customerType === 'COMPANY') {
      const name = [c.firstName, c.lastName].filter(Boolean).join(' ').trim();
      return name ? name : '';
    }
    return '';
  }

  customerAddress(c: Customer): string {
    const parts = [c.addressLine1, c.addressLine2, [c.postalCode, c.city].filter(Boolean).join(' ')].filter(Boolean);
    const country = c.countryCode === 'FR' ? 'France' : c.countryCode;
    if (country && parts.length) {
      parts.push(country);
    }
    return parts.join(', ');
  }

  customerTitle(c: Customer): string {
    return customerDisplayName(c);
  }

  formatMoney(value: number): string {
    return formatPriceHt(value);
  }

  formatRate(rate: number): string {
    return formatRate(rate);
  }

  isPrint(): boolean {
    return this.viewMode() === 'print';
  }

  onPrint(): void {
    window.print();
  }

  startSigning(event: MouseEvent | TouchEvent): void {
    if (this.embedded()) {
      return;
    }
    const canvas = this.canvasRef()?.nativeElement;
    if (!canvas) {
      return;
    }
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return;
    }
    const pt = this.pointer(canvas, event);
    ctx.beginPath();
    ctx.moveTo(pt.x, pt.y);
    ctx.strokeStyle = this.isPrint() ? '#0f172a' : '#06b6d4';
    ctx.lineWidth = 2;
    ctx.lineCap = 'round';
    this.signing = true;
  }

  drawSignature(event: MouseEvent | TouchEvent): void {
    if (!this.signing || this.embedded()) {
      return;
    }
    const canvas = this.canvasRef()?.nativeElement;
    if (!canvas) {
      return;
    }
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return;
    }
    const pt = this.pointer(canvas, event);
    ctx.lineTo(pt.x, pt.y);
    ctx.stroke();
    this.hasSignature.set(true);
    if (!this.signatureDate()) {
      this.signatureDate.set(new Date().toLocaleDateString('fr-FR'));
    }
  }

  stopSigning(): void {
    this.signing = false;
  }

  clearSignature(): void {
    const canvas = this.canvasRef()?.nativeElement;
    if (!canvas) {
      return;
    }
    const ctx = canvas.getContext('2d');
    ctx?.clearRect(0, 0, canvas.width, canvas.height);
    this.hasSignature.set(false);
    this.signatureDate.set('');
  }

  private pointer(canvas: HTMLCanvasElement, event: MouseEvent | TouchEvent): { x: number; y: number } {
    const rect = canvas.getBoundingClientRect();
    const clientX = 'touches' in event ? event.touches[0]?.clientX : event.clientX;
    const clientY = 'touches' in event ? event.touches[0]?.clientY : event.clientY;
    return { x: (clientX ?? 0) - rect.left, y: (clientY ?? 0) - rect.top };
  }

  private resizeCanvas(): void {
    const canvas = this.canvasRef()?.nativeElement;
    if (!canvas) {
      return;
    }
    const ratio = window.devicePixelRatio || 1;
    canvas.width = 280 * ratio;
    canvas.height = 110 * ratio;
    const ctx = canvas.getContext('2d');
    ctx?.scale(ratio, ratio);
  }
}
