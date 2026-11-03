import { Component, computed, effect, inject, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { startWith } from 'rxjs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { CatalogPackagesApiService } from '../products/catalog-packages-api.service';
import { ProductCategoriesApiService } from '../products/product-categories-api.service';
import {
  formatPriceHt,
  loadCategoryColorMap,
  packageBadgeClasses,
  parseBillingFromDescription,
  productShortDescription,
  resolveCategoryColor,
} from '../products/product-catalog.util';
import { ProductsApiService } from '../products/products-api.service';
import { CustomersApiService } from '../customers/customers-api.service';
import { Customer, customerDisplayName } from '../../shared/models/customer.model';
import { CatalogPackage } from '../../shared/models/catalog-package.model';
import { Product } from '../../shared/models/product.model';
import { ProductCategory } from '../../shared/models/product-category.model';
import { Quotation, QuotationPayload } from '../../shared/models/quotation.model';
import { formatMoney, lineTotals } from '../../shared/utils/money.util';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { QuotationsApiService } from './quotations-api.service';
import { QuotationDocumentPreviewComponent, QuotationPreviewViewMode } from './quotation-document-preview.component';
import { QuotationPreviewLine } from './quotation-preview.model';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

type CatalogTab = 'products' | 'packages';

@Component({
  selector: 'app-quotations-editor-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ErrorBannerComponent,
    LoadingStateComponent,
    LucideIconComponent,
    QuotationDocumentPreviewComponent,
  ],
  templateUrl: './quotations-editor-page.component.html',
})
export class QuotationsEditorPageComponent implements OnInit {
  private readonly api = inject(QuotationsApiService);
  private readonly customersApi = inject(CustomersApiService);
  private readonly productsApi = inject(ProductsApiService);
  private readonly categoriesApi = inject(ProductCategoriesApiService);
  private readonly packagesApi = inject(CatalogPackagesApiService);
  private readonly workspaces = inject(WorkspaceService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  quotationId: string | null = null;
  readonly loading = signal(false);
  readonly catalogLoading = signal(false);
  readonly saving = signal(false);
  readonly actionBusy = signal(false);
  readonly error = signal<string | null>(null);
  readonly quotation = signal<Quotation | null>(null);
  readonly customers = signal<Customer[]>([]);
  readonly products = signal<Product[]>([]);
  readonly categories = signal<ProductCategory[]>([]);
  readonly packages = signal<CatalogPackage[]>([]);
  readonly categoryColorMap = signal(loadCategoryColorMap());

  readonly catalogTab = signal<CatalogTab>('products');
  readonly clientSearch = signal('');
  readonly clientDropdownOpen = signal(false);
  readonly catalogSearch = signal('');
  readonly catalogCategoryId = signal('');
  readonly pdfPreviewOpen = signal(false);
  readonly previewViewMode = signal<QuotationPreviewViewMode>('screen');
  readonly lineEditorOpen = signal(true);
  readonly toast = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    customerId: ['', Validators.required],
    issueDate: [''],
    validUntil: [''],
    notes: [''],
    lines: this.fb.array([this.newLineGroup()]),
  });

  /** Reactive Forms ne notifie pas les `computed` : on bridge via un signal. */
  private readonly customerId = toSignal(
    this.form.controls.customerId.valueChanges.pipe(startWith(this.form.controls.customerId.value)),
    { initialValue: '' },
  );

  readonly selectedCustomer = computed((): Customer | null => {
    const id = this.customerId();
    if (!id) {
      return null;
    }
    return this.customers().find((c) => c.id === id) ?? null;
  });

  readonly issuerWorkspace = computed(() => {
    const q = this.quotation();
    const list = this.workspaces.workspaces();
    if (q?.workspaceId) {
      return list.find((w) => w.id === q.workspaceId) ?? this.workspaces.selectedWorkspace();
    }
    return this.workspaces.selectedWorkspace();
  });

  readonly filteredClients = computed(() => {
    const q = this.clientSearch().trim().toLowerCase();
    if (!q) {
      return this.customers().filter((c) => !c.archived).slice(0, 12);
    }
    return this.customers()
      .filter((c) => !c.archived)
      .filter((c) => {
        const haystack = [
          customerDisplayName(c),
          c.email ?? '',
          c.phone ?? '',
          c.companyName ?? '',
          c.city ?? '',
          c.postalCode ?? '',
        ]
          .join(' ')
          .toLowerCase();
        return haystack.includes(q);
      })
      .slice(0, 12);
  });

  readonly filteredCatalogProducts = computed(() => {
    const q = this.catalogSearch().trim().toLowerCase();
    const catId = this.catalogCategoryId();
    return this.products().filter((p) => {
      if (!p.active) {
        return false;
      }
      const matchesCat = !catId || p.categoryId === catId;
      const matchesQ =
        !q ||
        p.name.toLowerCase().includes(q) ||
        p.referenceCode.toLowerCase().includes(q) ||
        (p.description?.toLowerCase().includes(q) ?? false);
      return matchesCat && matchesQ;
    });
  });

  readonly lineCount = computed(() => this.lines.length);

  readonly isEditable = computed(() => {
    const q = this.quotation();
    return !q || q.status === 'DRAFT';
  });

  constructor() {
    effect(() => {
      const ws = this.workspaces.selectedId();
      const gen = this.workspaces.generation();
      if (!ws) {
        return;
      }
      this.workspaces.refreshWorkspace(ws).subscribe();
      this.loadCatalog(ws, gen);
      this.customersApi.search(ws, { size: 500 }).subscribe({
        next: (page) => {
          if (this.workspaces.isStale(gen)) {
            return;
          }
          this.customers.set(page.content);
        },
      });
    });
  }

  get lines(): FormArray {
    return this.form.controls.lines;
  }

  ngOnInit(): void {
    this.quotationId = this.route.snapshot.paramMap.get('id');
    const isNew = this.route.snapshot.url.some((s) => s.path === 'new');
    if (this.quotationId && !isNew) {
      this.loadQuotation(this.quotationId);
    } else {
      this.quotationId = null;
      this.setDefaultDates();
    }
  }

  setCatalogTab(tab: CatalogTab): void {
    this.catalogTab.set(tab);
  }

  editable(): boolean {
    return this.isEditable();
  }

  draftLabel(): string {
    const q = this.quotation();
    if (q?.number) {
      return q.number;
    }
    return 'Brouillon (non enregistré)';
  }

  statusLabel(): string {
    const q = this.quotation();
    if (!q) {
      return 'Brouillon';
    }
    switch (q.status) {
      case 'DRAFT':
        return 'Brouillon';
      case 'SENT':
        return 'Envoyé';
      case 'ACCEPTED':
        return 'Accepté';
      case 'REJECTED':
        return 'Refusé';
      case 'EXPIRED':
        return 'Expiré';
      case 'CONVERTED':
        return 'Converti';
    }
  }

  customerLabel(c: Customer): string {
    return customerDisplayName(c);
  }

  categoryTheme(cat: ProductCategory | undefined): { bg: string; text: string; border: string; hex: string } {
    return resolveCategoryColor(cat, this.categoryColorMap());
  }

  categoryById(id: string | null | undefined): ProductCategory | undefined {
    if (!id) {
      return undefined;
    }
    return this.categories().find((c) => c.id === id);
  }

  productById(id: string | null | undefined): Product | undefined {
    if (!id) {
      return undefined;
    }
    return this.products().find((p) => p.id === id);
  }

  issuerAddressLine(): string {
    const w = this.issuerWorkspace();
    if (!w) {
      return '';
    }
    const parts = [
      w.addressLine1,
      w.addressLine2,
      [w.postalCode, w.city].filter(Boolean).join(' '),
      w.region,
      w.countryCode === 'FR' ? 'France' : w.countryCode,
    ].filter(Boolean);
    return parts.join(', ');
  }

  productUnitLabel(product: Product): string {
    return parseBillingFromDescription(product.description).unitLabel;
  }

  billingLabel(product: Product): string {
    const bill = parseBillingFromDescription(product.description);
    switch (bill.type) {
      case 'DAYS':
        return `TJM / ${bill.unitLabel}`;
      case 'RECURRING':
        return `Récurrent / ${bill.unitLabel}`;
      case 'FIXED_PRICE':
        return 'Forfait';
      default:
        return bill.unitLabel;
    }
  }

  unitLabelForLine(index: number): string {
    const productId = this.lines.at(index).get('productId')?.value as string;
    const p = this.productById(productId);
    if (p) {
      return parseBillingFromDescription(p.description).unitLabel;
    }
    return 'unité';
  }

  shortDescription(product: Product): string {
    return productShortDescription(product.description);
  }

  priceFormatted(value: number): string {
    return formatPriceHt(value);
  }

  packageBadge(theme: string): string {
    return packageBadgeClasses(theme);
  }

  isProductInCart(productId: string): boolean {
    return this.lines.controls.some((c) => c.get('productId')?.value === productId);
  }

  lineQuantity(index: number): number {
    return Number(this.lines.at(index).get('quantity')?.value) || 0;
  }

  lineUnitPrice(index: number): number {
    return Number(this.lines.at(index).get('unitPriceHt')?.value) || 0;
  }

  lineDescription(index: number): string {
    return (this.lines.at(index).get('description')?.value as string) ?? '';
  }

  lineProductRef(index: number): string | null {
    const productId = this.lines.at(index).get('productId')?.value as string;
    return this.productById(productId)?.referenceCode ?? null;
  }

  lineCategory(index: number): ProductCategory | undefined {
    const productId = this.lines.at(index).get('productId')?.value as string;
    return this.categoryById(this.productById(productId)?.categoryId);
  }

  lineHt(index: number): string {
    const g = this.lines.at(index).getRawValue();
    return formatMoney(lineTotals(Number(g.quantity), Number(g.unitPriceHt), Number(g.vatRate)).ht);
  }

  displayTotals(): { ht: string; vat: string; ttc: string; htNum: number; vatNum: number; ttcNum: number } {
    const raw = this.lines.getRawValue();
    let ht = 0;
    let vat = 0;
    let ttc = 0;
    for (const l of raw) {
      const t = lineTotals(Number(l.quantity), Number(l.unitPriceHt), Number(l.vatRate));
      ht += t.ht;
      vat += t.vat;
      ttc += t.ttc;
    }
    return {
      ht: formatMoney(ht),
      vat: formatMoney(vat),
      ttc: formatMoney(ttc),
      htNum: ht,
      vatNum: vat,
      ttcNum: ttc,
    };
  }

  onClientSearchInput(value: string): void {
    this.clientSearch.set(value);
    this.clientDropdownOpen.set(true);
  }

  openClientDropdown(): void {
    this.clientDropdownOpen.set(true);
  }

  closeClientDropdown(): void {
    this.clientDropdownOpen.set(false);
  }

  selectCustomer(c: Customer): void {
    if (!this.editable()) {
      return;
    }
    this.form.patchValue({ customerId: c.id });
    this.clientSearch.set('');
    this.clientDropdownOpen.set(false);
    this.notify(`Client mis à jour : ${this.customerLabel(c)}`);
  }

  addProductToQuote(product: Product): void {
    if (!this.editable()) {
      return;
    }
    const idx = this.lines.controls.findIndex((c) => c.get('productId')?.value === product.id);
    if (idx >= 0) {
      const ctrl = this.lines.at(idx);
      const qty = Number(ctrl.get('quantity')?.value) || 1;
      ctrl.patchValue({ quantity: qty + 1 });
    } else {
      if (this.lines.length === 1 && !this.lines.at(0).get('description')?.value) {
        this.lines.removeAt(0);
      }
      this.lines.push(
        this.fb.nonNullable.group({
          productId: [product.id],
          description: [product.name, Validators.required],
          quantity: [1, [Validators.required, Validators.min(0.0001)]],
          unitPriceHt: [product.unitPriceHt, [Validators.required, Validators.min(0)]],
          vatRate: [product.vatRate, [Validators.required, Validators.min(0)]],
        }),
      );
    }
    this.notify(`« ${product.name} » ajouté au devis`);
  }

  injectPackage(pkg: CatalogPackage): void {
    if (!this.editable()) {
      return;
    }
    for (const item of pkg.items) {
      const product = this.productById(item.productId);
      if (!product) {
        continue;
      }
      const idx = this.lines.controls.findIndex((c) => c.get('productId')?.value === product.id);
      if (idx >= 0) {
        const ctrl = this.lines.at(idx);
        const qty = Number(ctrl.get('quantity')?.value) || 0;
        ctrl.patchValue({ quantity: qty + item.quantity });
      } else {
        if (this.lines.length === 1 && !this.lines.at(0).get('description')?.value) {
          this.lines.removeAt(0);
        }
        this.lines.push(
          this.fb.nonNullable.group({
            productId: [product.id],
            description: [product.name, Validators.required],
            quantity: [item.quantity, [Validators.required, Validators.min(0.0001)]],
            unitPriceHt: [product.unitPriceHt, [Validators.required, Validators.min(0)]],
            vatRate: [product.vatRate, [Validators.required, Validators.min(0)]],
          }),
        );
      }
    }
    this.notify(`Package « ${pkg.title} » injecté dans le devis`);
  }

  bumpQuantity(index: number, delta: number): void {
    if (!this.editable()) {
      return;
    }
    const ctrl = this.lines.at(index);
    const qty = Math.max(0.0001, (Number(ctrl.get('quantity')?.value) || 1) + delta);
    ctrl.patchValue({ quantity: qty });
  }

  setQuantity(index: number, raw: string): void {
    if (!this.editable()) {
      return;
    }
    const qty = Math.max(0.0001, parseFloat(raw) || 1);
    this.lines.at(index).patchValue({ quantity: qty });
  }

  removeLine(index: number): void {
    if (!this.editable()) {
      return;
    }
    this.lines.removeAt(index);
    if (!this.lines.length) {
      this.lines.push(this.newLineGroup());
    }
  }

  openPdfPreview(): void {
    this.previewViewMode.set('screen');
    this.pdfPreviewOpen.set(true);
  }

  closePdfPreview(): void {
    this.pdfPreviewOpen.set(false);
  }

  setPreviewViewMode(mode: QuotationPreviewViewMode): void {
    this.previewViewMode.set(mode);
  }

  printPreview(): void {
    const source = document.querySelector('.quotation-print-root');
    if (!(source instanceof HTMLElement)) {
      return;
    }

    const existing = document.getElementById('quotation-print-clone');
    existing?.remove();

    const clone = source.cloneNode(true) as HTMLElement;
    clone.id = 'quotation-print-clone';
    clone.removeAttribute('class');
    clone.className = source.className.replace(/\bquotation-print-root\b/g, '').trim();
    clone.classList.add('quotation-print-clone');

    document.body.appendChild(clone);
    document.body.classList.add('quotation-print-active');

    let cleaned = false;
    const cleanup = (): void => {
      if (cleaned) {
        return;
      }
      cleaned = true;
      clone.remove();
      document.body.classList.remove('quotation-print-active');
      window.removeEventListener('afterprint', cleanup);
    };
    window.addEventListener('afterprint', cleanup);
    window.setTimeout(cleanup, 30_000);

    requestAnimationFrame(() => window.print());
  }

  previewStatusBadge(): string {
    const q = this.quotation();
    if (!q) {
      return "En attente d'approbation";
    }
    switch (q.status) {
      case 'DRAFT':
        return "En attente d'approbation";
      case 'SENT':
        return 'Devis envoyé';
      case 'ACCEPTED':
        return 'Devis accepté';
      case 'REJECTED':
        return 'Devis refusé';
      case 'EXPIRED':
        return 'Devis expiré';
      case 'CONVERTED':
        return 'Converti en facture';
    }
  }

  previewNotesParts(): { subject: string; summary: string | null } {
    const notes = (this.form.controls.notes.value ?? '').trim();
    if (!notes) {
      return {
        subject: 'Prestations et services',
        summary: 'Détail des prestations listées dans le tableau ci-dessous.',
      };
    }
    const parts = notes.split('\n').map((l) => l.trim()).filter(Boolean);
    return {
      subject: parts[0] ?? 'Prestations et services',
      summary: parts.slice(1).join(' ') || null,
    };
  }

  previewLines(): QuotationPreviewLine[] {
    const rows: QuotationPreviewLine[] = [];
    for (let i = 0; i < this.lines.length; i++) {
      const label = this.lineDescription(i);
      if (!label) {
        continue;
      }
      const productId = this.lines.at(i).get('productId')?.value as string;
      const product = this.productById(productId);
      const quantity = this.lineQuantity(i);
      const unitPriceHt = this.lineUnitPrice(i);
      rows.push({
        sku: this.lineProductRef(i),
        label,
        detail: product ? this.shortDescription(product) : null,
        quantity,
        unit: this.unitLabelForLine(i),
        unitPriceHt,
        vatRate: Number(this.lines.at(i).get('vatRate')?.value) || 0,
        lineTotalHt: quantity * unitPriceHt,
      });
    }
    return rows;
  }

  startNewQuotation(): void {
    void this.router.navigate(['/quotations/new']);
    this.quotation.set(null);
    this.quotationId = null;
    this.form.reset({
      customerId: '',
      issueDate: '',
      validUntil: '',
      notes: '',
    });
    this.lines.clear();
    this.lines.push(this.newLineGroup());
    this.setDefaultDates();
    this.notify('Nouveau devis initialisé');
  }

  saveDraft(): void {
    if (this.form.invalid || this.saving() || !this.editable()) {
      return;
    }
    const payload = this.buildPayload();
    this.saving.set(true);
    this.error.set(null);
    const req = this.quotationId ? this.api.update(this.quotationId, payload) : this.api.create(payload);
    req.subscribe({
      next: (q) => {
        this.quotation.set(q);
        if (!this.quotationId) {
          void this.router.navigate(['/quotations', q.id]);
        }
        this.quotationId = q.id;
        this.saving.set(false);
        this.notify('Devis enregistré');
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Enregistrement impossible'));
        this.saving.set(false);
      },
    });
  }

  saveAndSend(): void {
    if (this.form.invalid || this.actionBusy()) {
      return;
    }
    this.saveDraftInternal(() => {
      if (!this.quotationId) {
        return;
      }
      this.actionBusy.set(true);
      this.api.send(this.quotationId).subscribe({
        next: (q) => {
          this.quotation.set(q);
          this.form.disable();
          this.actionBusy.set(false);
          this.notify('Devis marqué comme envoyé');
        },
        error: (err) => {
          this.error.set(apiErrorMessage(err, 'Envoi impossible'));
          this.actionBusy.set(false);
        },
      });
    });
  }

  runAction(kind: 'send' | 'accept' | 'convert'): void {
    if (!this.quotationId || this.actionBusy()) {
      return;
    }
    this.actionBusy.set(true);
    this.error.set(null);
    const id = this.quotationId;
    const done = () => this.actionBusy.set(false);
    if (kind === 'send') {
      this.api.send(id).subscribe({
        next: (q) => {
          this.quotation.set(q);
          this.form.disable();
          done();
        },
        error: (err) => {
          this.error.set(apiErrorMessage(err, 'Envoi impossible'));
          done();
        },
      });
      return;
    }
    if (kind === 'accept') {
      this.api.accept(id).subscribe({
        next: (q) => {
          this.quotation.set(q);
          done();
        },
        error: (err) => {
          this.error.set(apiErrorMessage(err, 'Acceptation impossible'));
          done();
        },
      });
      return;
    }
    this.api.convertToInvoice(id).subscribe({
      next: (inv) => {
        done();
        void this.router.navigate(['/invoices', inv.id]);
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Conversion impossible'));
        this.loadQuotation(id);
        done();
      },
    });
  }

  private saveDraftInternal(done: () => void): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    const payload = this.buildPayload();
    this.saving.set(true);
    this.error.set(null);
    const req = this.quotationId ? this.api.update(this.quotationId, payload) : this.api.create(payload);
    req.subscribe({
      next: (q) => {
        this.quotation.set(q);
        if (!this.quotationId) {
          this.quotationId = q.id;
          void this.router.navigate(['/quotations', q.id], { replaceUrl: true });
        }
        this.saving.set(false);
        done();
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Enregistrement impossible'));
        this.saving.set(false);
      },
    });
  }

  private loadCatalog(workspaceId: string, gen: number): void {
    this.catalogLoading.set(true);
    this.productsApi.search(workspaceId, { activeOnly: true, size: 500 }).subscribe({
      next: (page) => {
        if (this.workspaces.isStale(gen)) {
          return;
        }
        this.products.set(page.content);
      },
    });
    this.categoriesApi.list(workspaceId).subscribe({
      next: (list) => {
        if (this.workspaces.isStale(gen)) {
          return;
        }
        this.categories.set(list);
      },
    });
    this.packagesApi.list(workspaceId).subscribe({
      next: (list) => {
        if (this.workspaces.isStale(gen)) {
          return;
        }
        this.packages.set(list);
        this.catalogLoading.set(false);
      },
      error: () => {
        if (!this.workspaces.isStale(gen)) {
          this.packages.set([]);
          this.catalogLoading.set(false);
        }
      },
    });
  }

  private loadQuotation(id: string): void {
    this.loading.set(true);
    this.api.get(id).subscribe({
      next: (q) => {
        this.quotation.set(q);
        this.patchForm(q);
        this.workspaces.refreshWorkspace(q.workspaceId).subscribe();
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Devis introuvable'));
        this.loading.set(false);
      },
    });
  }

  private patchForm(q: Quotation): void {
    this.lines.clear();
    const source = q.lines.length
      ? q.lines
      : [{ productId: null as string | null, description: '', quantity: 1, unitPriceHt: 0, vatRate: 20 }];
    for (const line of source) {
      this.lines.push(
        this.fb.nonNullable.group({
          productId: [line.productId ?? ''],
          description: [line.description, Validators.required],
          quantity: [line.quantity, [Validators.required, Validators.min(0.0001)]],
          unitPriceHt: [line.unitPriceHt, [Validators.required, Validators.min(0)]],
          vatRate: [line.vatRate, [Validators.required, Validators.min(0)]],
        }),
      );
    }
    this.form.patchValue({
      customerId: q.customerId,
      issueDate: q.issueDate ?? '',
      validUntil: q.validUntil ?? '',
      notes: q.notes ?? '',
    });
    if (q.status !== 'DRAFT') {
      this.form.disable();
    } else {
      this.form.enable();
    }
  }

  private buildPayload(): QuotationPayload {
    return {
      workspaceId: this.workspaces.requireSelectedId(),
      customerId: this.form.value.customerId!,
      issueDate: this.form.value.issueDate || null,
      validUntil: this.form.value.validUntil || null,
      notes: this.form.value.notes || null,
      lines: this.lines
        .getRawValue()
        .filter((l) => l.description?.trim())
        .map((l) => ({
          productId: l.productId || null,
          description: l.description.trim(),
          quantity: Number(l.quantity),
          unitPriceHt: Number(l.unitPriceHt),
          vatRate: Number(l.vatRate),
        })),
    };
  }

  private newLineGroup() {
    return this.fb.nonNullable.group({
      productId: [''],
      description: ['', Validators.required],
      quantity: [1, [Validators.required, Validators.min(0.0001)]],
      unitPriceHt: [0, [Validators.required, Validators.min(0)]],
      vatRate: [20, [Validators.required, Validators.min(0)]],
    });
  }

  private setDefaultDates(): void {
    const today = new Date();
    const valid = new Date(today);
    valid.setDate(valid.getDate() + 30);
    this.form.patchValue({
      issueDate: today.toISOString().slice(0, 10),
      validUntil: valid.toISOString().slice(0, 10),
    });
  }

  private notify(message: string): void {
    this.toast.set(message);
    setTimeout(() => this.toast.set(null), 3000);
  }
}
