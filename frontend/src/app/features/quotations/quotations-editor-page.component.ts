import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, OnInit, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin, of, startWith, Subject } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, switchMap, tap } from 'rxjs/operators';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { CatalogPackagesApiService } from '../products/catalog-packages-api.service';
import { ProductCategoriesApiService } from '../products/product-categories-api.service';
import {
  billingSummary,
  formatPriceHt,
  loadCategoryColorMap,
  packageBadgeClasses,
  productShortDescription,
  resolveCategoryColor,
} from '../products/product-catalog.util';
import { ProductsApiService } from '../products/products-api.service';
import { CustomersApiService } from '../customers/customers-api.service';
import { Customer, customerDisplayName } from '../../shared/models/customer.model';
import { CatalogPackage } from '../../shared/models/catalog-package.model';
import { emptyPage } from '../../shared/models/page.model';
import { Product } from '../../shared/models/product.model';
import { SEARCH_SELECT_LIMIT } from '../../shared/ui/search-select.component';
import { ProductCategory } from '../../shared/models/product-category.model';
import { Quotation, QuotationPayload } from '../../shared/models/quotation.model';
import { formatMoney, lineTotals, vatBreakdown } from '../../shared/utils/money.util';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { DocumentEmailDialogComponent } from '../../shared/ui/document-email-dialog.component';
import { DocumentEmailPayload } from '../../shared/models/invoice.model';
import { QuotationsApiService } from './quotations-api.service';
import { QuotationDocumentPreviewComponent, QuotationPreviewViewMode } from './quotation-document-preview.component';
import { QuotationPreviewLine, QuotationPreviewTotals } from './quotation-preview.model';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

type CatalogTab = 'products' | 'packages';

/** Nombre de prestations affichées dans le catalogue de l'éditeur ; au-delà, l'utilisateur filtre. */
const CATALOG_PAGE_SIZE = 50;

@Component({
  selector: 'app-quotations-editor-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    ErrorBannerComponent,
    LoadingStateComponent,
    LucideIconComponent,
    QuotationDocumentPreviewComponent,
    DocumentEmailDialogComponent,
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
  readonly downloadingPdf = signal(false);
  readonly error = signal<string | null>(null);
  readonly quotation = signal<Quotation | null>(null);
  /** Résultats de la recherche client courante (serveur). */
  readonly customers = signal<Customer[]>([]);
  /** Résultats de la recherche catalogue courante (serveur, produits actifs). */
  readonly products = signal<Product[]>([]);
  /** Tous les produits rencontrés (recherches, lignes chargées, packages) pour résoudre les lignes. */
  private readonly knownProducts = signal<ReadonlyMap<string, Product>>(new Map());
  /** Client sélectionné chargé par identifiant quand il n'est pas dans les résultats courants. */
  private readonly resolvedCustomer = signal<Customer | null>(null);
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
  readonly emailDialogOpen = signal(false);
  readonly emailBusy = signal(false);
  readonly emailError = signal<string | null>(null);

  /** Acompte proposé par défaut sur un nouveau devis (% du TTC). */
  private static readonly DEFAULT_DEPOSIT_PERCENT = 30;

  readonly form = this.fb.nonNullable.group({
    customerId: ['', Validators.required],
    issueDate: [''],
    validUntil: [''],
    notes: [''],
    discountPercent: [0, [Validators.min(0), Validators.max(100)]],
    depositPercent: [
      QuotationsEditorPageComponent.DEFAULT_DEPOSIT_PERCENT,
      [Validators.min(0), Validators.max(100)],
    ],
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
    const resolved = this.resolvedCustomer();
    if (resolved?.id === id) {
      return resolved;
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

  /** Résultats client (recherche serveur, non archivés). */
  readonly filteredClients = computed(() => this.customers().filter((c) => !c.archived));
  readonly clientSearching = signal(false);

  /** Résultats catalogue (recherche + catégorie côté serveur). */
  readonly filteredCatalogProducts = computed(() => this.products());
  readonly catalogSearching = signal(false);

  private readonly clientSearch$ = new Subject<string>();
  private readonly catalogSearch$ = new Subject<{ q: string; categoryId: string }>();

  readonly lineCount = computed(() => this.lines.length);

  readonly isEditable = computed(() => {
    const q = this.quotation();
    return !q || q.status === 'DRAFT';
  });

  constructor() {
    this.clientSearch$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        tap(() => this.clientSearching.set(true)),
        switchMap((q) =>
          this.customersApi
            .search(this.workspaces.requireSelectedId(), {
              q: q || undefined,
              size: SEARCH_SELECT_LIMIT,
              sort: 'companyName,lastName,firstName,asc',
            })
            .pipe(catchError(() => of(emptyPage<Customer>()))),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.customers.set(page.content);
        this.clientSearching.set(false);
      });

    this.catalogSearch$
      .pipe(
        debounceTime(250),
        distinctUntilChanged((a, b) => a.q === b.q && a.categoryId === b.categoryId),
        tap(() => this.catalogSearching.set(true)),
        switchMap(({ q, categoryId }) =>
          this.productsApi
            .search(this.workspaces.requireSelectedId(), {
              q: q || undefined,
              categoryId: categoryId || undefined,
              activeOnly: true,
              size: CATALOG_PAGE_SIZE,
              sort: 'name,asc',
            })
            .pipe(catchError(() => of(emptyPage<Product>()))),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.products.set(page.content);
        this.rememberProducts(page.content);
        this.catalogSearching.set(false);
      });

    effect(() => {
      const ws = this.workspaces.selectedId();
      const gen = this.workspaces.generation();
      if (!ws) {
        return;
      }
      this.workspaces.refreshWorkspace(ws).subscribe();
      this.loadCatalog(ws, gen);
      this.clientSearch$.next(this.clientSearch().trim());
      this.catalogSearch$.next({ q: this.catalogSearch().trim(), categoryId: this.catalogCategoryId() });
    });

    // Client positionné (chargement d'un devis) sans être dans les résultats : on le charge par id.
    effect(() => {
      const id = this.customerId();
      const current = this.resolvedCustomer();
      if (!id || current?.id === id) {
        return;
      }
      const inResults = untracked(() => this.customers().find((c) => c.id === id));
      if (inResults) {
        this.resolvedCustomer.set(inResults);
        return;
      }
      this.customersApi.get(id).subscribe({
        next: (c) => this.resolvedCustomer.set(c),
        error: () => undefined,
      });
    });
  }

  onCatalogSearchInput(value: string): void {
    this.catalogSearch.set(value);
    this.catalogSearch$.next({ q: value.trim(), categoryId: this.catalogCategoryId() });
  }

  onCatalogCategoryChange(value: string): void {
    this.catalogCategoryId.set(value);
    this.catalogSearch$.next({ q: this.catalogSearch().trim(), categoryId: value });
  }

  private rememberProducts(list: readonly Product[]): void {
    if (!list.length) {
      return;
    }
    this.knownProducts.update((map) => {
      const next = new Map(map);
      for (const p of list) {
        next.set(p.id, p);
      }
      return next;
    });
  }

  /** Charge par identifiant les produits référencés mais encore inconnus (lignes, packages). */
  private ensureProductsLoaded(ids: readonly (string | null | undefined)[], done?: () => void): void {
    const missing = [...new Set(ids.filter((id): id is string => !!id && !this.knownProducts().has(id)))];
    if (!missing.length) {
      done?.();
      return;
    }
    forkJoin(missing.map((id) => this.productsApi.get(id).pipe(catchError(() => of(null))))).subscribe((list) => {
      this.rememberProducts(list.filter((p): p is Product => !!p));
      done?.();
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

  /** Ouvre la modale d'envoi ; un brouillon modifié est d'abord enregistré. */
  openEmailDialog(): void {
    this.emailError.set(null);
    const q = this.quotation();
    if (q && q.status !== 'DRAFT') {
      this.emailDialogOpen.set(true);
      return;
    }
    if (this.form.dirty || !this.quotationId) {
      this.saveDraftInternal(() => this.emailDialogOpen.set(true));
      return;
    }
    this.emailDialogOpen.set(true);
  }

  sendEmail(payload: DocumentEmailPayload): void {
    if (!this.quotationId || this.emailBusy()) {
      return;
    }
    this.emailBusy.set(true);
    this.emailError.set(null);
    const id = this.quotationId;
    this.api.sendByEmail(id, payload).subscribe({
      next: (result) => {
        this.emailBusy.set(false);
        this.emailDialogOpen.set(false);
        this.notify(`Devis envoyé à ${result.to}`);
        this.loadQuotation(id);
      },
      error: (err) => {
        this.emailError.set(apiErrorMessage(err, 'Envoi impossible'));
        this.emailBusy.set(false);
      },
    });
  }

  rejectManually(): void {
    if (!this.quotationId || this.actionBusy()) {
      return;
    }
    const reason = window.prompt('Motif du refus (optionnel) :', '');
    if (reason === null) {
      return;
    }
    this.actionBusy.set(true);
    this.error.set(null);
    this.api.reject(this.quotationId, reason.trim() || null).subscribe({
      next: (q) => {
        this.quotation.set(q);
        this.actionBusy.set(false);
        this.notify('Devis marqué comme refusé');
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Refus impossible'));
        this.actionBusy.set(false);
      },
    });
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
    return this.knownProducts().get(id);
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
    return product.unitLabel;
  }

  billingLabel(product: Product): string {
    return billingSummary(product);
  }

  unitLabelForLine(index: number): string {
    const productId = this.lines.at(index).get('productId')?.value as string;
    return this.productById(productId)?.unitLabel ?? 'unité';
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
    return formatMoney(
      lineTotals(Number(g.quantity), Number(g.unitPriceHt), Number(g.vatRate), this.lineDiscount(index)).ht,
    );
  }

  /** Remise de ligne (%), bornée 0–100. */
  lineDiscount(index: number): number {
    return this.clampPercent(this.lines.at(index).get('discountPercent')?.value);
  }

  discountPercentValue(): number {
    return this.clampPercent(this.form.controls.discountPercent.value);
  }

  depositPercentValue(): number {
    return this.clampPercent(this.form.controls.depositPercent.value);
  }

  /**
   * Totaux affichés : sous-total brut, remise globale (même coefficient appliqué au HT et à la TVA,
   * comme `MoneyCalculator.applyDiscount` côté serveur), puis nets.
   */
  displayTotals(): QuotationPreviewTotals {
    const raw = this.lines.getRawValue();
    let grossHt = 0;
    let grossVat = 0;
    const rated: { vatRate: number; ht: number }[] = [];
    for (const l of raw) {
      const t = lineTotals(
        Number(l.quantity),
        Number(l.unitPriceHt),
        Number(l.vatRate),
        this.clampPercent(l.discountPercent),
      );
      grossHt += t.ht;
      grossVat += t.vat;
      rated.push({ vatRate: Number(l.vatRate) || 0, ht: t.ht });
    }
    const pct = this.discountPercentValue();
    const discount = Math.round(grossHt * pct) / 100;
    const ht = grossHt - discount;
    const vat = grossVat - Math.round(grossVat * pct) / 100;
    const ttc = ht + vat;
    return {
      subtotalHt: formatMoney(grossHt),
      discount: formatMoney(discount),
      ht: formatMoney(ht),
      vat: formatMoney(vat),
      ttc: formatMoney(ttc),
      subtotalHtNum: grossHt,
      discountNum: discount,
      htNum: ht,
      vatNum: vat,
      ttcNum: ttc,
      vatLines: vatBreakdown(rated, pct),
    };
  }

  private clampPercent(value: unknown): number {
    const n = Number(value);
    if (!Number.isFinite(n) || n <= 0) {
      return 0;
    }
    return Math.min(100, n);
  }

  onClientSearchInput(value: string): void {
    this.clientSearch.set(value);
    this.clientDropdownOpen.set(true);
    this.clientSearch$.next(value.trim());
  }

  openClientDropdown(): void {
    this.clientDropdownOpen.set(true);
    this.clientSearch$.next(this.clientSearch().trim());
  }

  closeClientDropdown(): void {
    this.clientDropdownOpen.set(false);
  }

  selectCustomer(c: Customer): void {
    if (!this.editable()) {
      return;
    }
    this.resolvedCustomer.set(c);
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
        this.lineGroup({
          productId: product.id,
          description: product.name,
          quantity: 1,
          unitPriceHt: product.unitPriceHt,
          vatRate: product.vatRate,
          discountPercent: 0,
        }),
      );
    }
    this.notify(`« ${product.name} » ajouté au devis`);
  }

  injectPackage(pkg: CatalogPackage): void {
    if (!this.editable()) {
      return;
    }
    this.ensureProductsLoaded(
      pkg.items.map((i) => i.productId),
      () => this.injectLoadedPackage(pkg),
    );
  }

  private injectLoadedPackage(pkg: CatalogPackage): void {
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
          this.lineGroup({
            productId: product.id,
            description: product.name,
            quantity: item.quantity,
            unitPriceHt: product.unitPriceHt,
            vatRate: product.vatRate,
            discountPercent: 0,
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

  /** Copie le devis courant en nouveau brouillon et l'ouvre. */
  duplicate(): void {
    if (!this.quotationId || this.actionBusy()) {
      return;
    }
    this.actionBusy.set(true);
    this.error.set(null);
    this.api.duplicate(this.quotationId).subscribe({
      next: (copy) => {
        this.actionBusy.set(false);
        this.quotationId = copy.id;
        this.quotation.set(copy);
        this.patchForm(copy);
        this.ensureProductsLoaded(copy.lines.map((l) => l.productId));
        void this.router.navigate(['/quotations', copy.id]);
        this.notify('Devis dupliqué : nouveau brouillon');
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Duplication impossible'));
        this.actionBusy.set(false);
      },
    });
  }

  /** Acompte facturable : devis accepté, acompte prévu, pas encore facturé. */
  canInvoiceDeposit(): boolean {
    const q = this.quotation();
    return !!q && q.status === 'ACCEPTED' && Number(q.depositPercent) > 0 && !q.depositInvoiceId;
  }

  createDepositInvoice(): void {
    if (!this.quotationId || this.actionBusy()) {
      return;
    }
    this.actionBusy.set(true);
    this.error.set(null);
    this.api.createDepositInvoice(this.quotationId).subscribe({
      next: (inv) => {
        this.actionBusy.set(false);
        void this.router.navigate(['/invoices', inv.id]);
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, "Facture d'acompte impossible"));
        this.actionBusy.set(false);
      },
    });
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

  /**
   * PDF généré côté serveur à partir du devis enregistré : tant que le devis est un brouillon,
   * on l'enregistre d'abord pour que le PDF reflète exactement l'écran (les ajouts depuis le
   * catalogue ne marquent pas le formulaire « dirty », d'où la sauvegarde systématique).
   */
  downloadPdf(): void {
    if (this.downloadingPdf() || this.saving()) {
      return;
    }
    if (this.editable()) {
      if (this.form.invalid) {
        this.error.set('Complétez le devis (client et lignes) avant de générer le PDF.');
        return;
      }
      this.saveDraftInternal(() => this.fetchPdf());
      return;
    }
    this.fetchPdf();
  }

  private fetchPdf(): void {
    const id = this.quotationId;
    if (!id) {
      return;
    }
    this.downloadingPdf.set(true);
    this.api.downloadPdf(id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `devis-${this.quotation()?.number ?? 'brouillon'}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
        this.downloadingPdf.set(false);
        this.notify('PDF généré');
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Génération du PDF impossible'));
        this.downloadingPdf.set(false);
      },
    });
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
      const vatRate = Number(this.lines.at(i).get('vatRate')?.value) || 0;
      const discountPercent = this.lineDiscount(i);
      rows.push({
        sku: this.lineProductRef(i),
        label,
        detail: product ? this.shortDescription(product) : null,
        quantity,
        unit: this.unitLabelForLine(i),
        unitPriceHt,
        vatRate,
        discountPercent,
        lineTotalHt: lineTotals(quantity, unitPriceHt, vatRate, discountPercent).ht,
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
      discountPercent: 0,
      depositPercent: QuotationsEditorPageComponent.DEFAULT_DEPOSIT_PERCENT,
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
        this.form.markAsPristine();
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
        this.form.markAsPristine();
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
        this.ensureProductsLoaded(q.lines.map((l) => l.productId));
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
      : [
          {
            productId: null as string | null,
            description: '',
            quantity: 1,
            unitPriceHt: 0,
            vatRate: 20,
            discountPercent: 0,
          },
        ];
    for (const line of source) {
      this.lines.push(
        this.lineGroup({
          productId: line.productId ?? '',
          description: line.description,
          quantity: line.quantity,
          unitPriceHt: line.unitPriceHt,
          vatRate: line.vatRate,
          discountPercent: Number(line.discountPercent ?? 0),
        }),
      );
    }
    this.form.patchValue({
      customerId: q.customerId,
      issueDate: q.issueDate ?? '',
      validUntil: q.validUntil ?? '',
      notes: q.notes ?? '',
      discountPercent: Number(q.discountPercent ?? 0),
      depositPercent: Number(q.depositPercent ?? 0),
    });
    if (q.status !== 'DRAFT') {
      this.form.disable();
    } else {
      this.form.enable();
    }
    this.form.markAsPristine();
  }

  private buildPayload(): QuotationPayload {
    return {
      workspaceId: this.workspaces.requireSelectedId(),
      customerId: this.form.value.customerId!,
      issueDate: this.form.value.issueDate || null,
      validUntil: this.form.value.validUntil || null,
      notes: this.form.value.notes || null,
      discountPercent: this.discountPercentValue(),
      depositPercent: this.depositPercentValue(),
      lines: this.lines
        .getRawValue()
        .filter((l) => l.description?.trim())
        .map((l) => ({
          productId: l.productId || null,
          description: l.description.trim(),
          quantity: Number(l.quantity),
          unitPriceHt: Number(l.unitPriceHt),
          vatRate: Number(l.vatRate),
          discountPercent: this.clampPercent(l.discountPercent),
        })),
    };
  }

  private newLineGroup() {
    return this.lineGroup({
      productId: '',
      description: '',
      quantity: 1,
      unitPriceHt: 0,
      vatRate: 20,
      discountPercent: 0,
    });
  }

  private lineGroup(v: {
    productId: string | null;
    description: string;
    quantity: number;
    unitPriceHt: number;
    vatRate: number;
    discountPercent: number;
  }) {
    return this.fb.nonNullable.group({
      productId: [v.productId ?? ''],
      description: [v.description, Validators.required],
      quantity: [v.quantity, [Validators.required, Validators.min(0.0001)]],
      unitPriceHt: [v.unitPriceHt, [Validators.required, Validators.min(0)]],
      vatRate: [v.vatRate, [Validators.required, Validators.min(0)]],
      discountPercent: [v.discountPercent, [Validators.min(0), Validators.max(100)]],
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
