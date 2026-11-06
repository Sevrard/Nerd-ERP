import { Component, computed, DestroyRef, effect, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, forkJoin, of, Subject } from 'rxjs';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { sortParam } from '../../shared/models/page.model';
import { BillingType, Product, ProductStats } from '../../shared/models/product.model';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { IconActionComponent } from '../../shared/ui/icon-action.component';
import { LucideIconComponent } from '../../shared/ui/lucide-icon.component';
import { PaginatorComponent } from '../../shared/ui/paginator.component';
import { SearchSelectComponent } from '../../shared/ui/search-select.component';
import { PageState } from '../../shared/utils/page-state';
import { productPickerSource } from './product-picker.util';
import { ProductsApiService } from './products-api.service';
import { ProductCategoriesApiService } from './product-categories-api.service';
import { CatalogPackagesApiService } from './catalog-packages-api.service';
import { ProductCategory } from '../../shared/models/product-category.model';
import {
  CatalogPackage,
  CatalogPackageItemPayload,
  PackageDiscountType,
} from '../../shared/models/catalog-package.model';
import { apiErrorMessage } from '../../shared/utils/api-error.util';
import { nextSortDirection, sortMultiplier, SortDirection } from '../../shared/utils/table-sort.util';
import { SortableThComponent } from '../../shared/ui/sortable-th.component';
import {
  BILLING_TYPE_OPTIONS,
  CATEGORY_COLOR_PALETTE,
  CategoryColorTheme,
  defaultUnitLabel,
  formatPriceHt,
  loadCategoryColorMap,
  packageBadgeClasses,
  productShortDescription,
  resolveCategoryColor,
  saveCategoryColor,
} from './product-catalog.util';

type CatalogTab = 'products' | 'categories' | 'packages';

type ProductSortKey = 'referenceCode' | 'name' | 'category' | 'price' | 'vat';

/**
 * Colonnes UI → propriétés JPA triables côté serveur.
 * « Catégorie » trie par identifiant (regroupe les produits d'une même catégorie), pas par libellé.
 */
const PRODUCT_SORT_PROPERTIES: Record<ProductSortKey, string> = {
  referenceCode: 'referenceCode',
  name: 'name',
  category: 'categoryId,name',
  price: 'unitPriceHt',
  vat: 'vatRate',
};

const EMPTY_PRODUCT_STATS: ProductStats = {
  activeProducts: 0,
  averageDayRateHt: 0,
  catalogValueHt: 0,
  productsByCategory: {},
};

type CategorySortKey = 'code' | 'label' | 'sortOrder' | 'products';

interface ProductInlineDraft {
  name: string;
  categoryId: string;
  billingType: BillingType;
  /** Vide = libellé par défaut du mode. */
  unitLabel: string;
  unitPriceHt: number;
  vatRate: number;
}

@Component({
  selector: 'app-products-list-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    LoadingStateComponent,
    EmptyStateComponent,
    ErrorBannerComponent,
    IconActionComponent,
    LucideIconComponent,
    SortableThComponent,
    PaginatorComponent,
    SearchSelectComponent,
  ],
  templateUrl: './products-list-page.component.html',
})
export class ProductsListPageComponent implements OnInit {
  private readonly api = inject(ProductsApiService);
  private readonly categoriesApi = inject(ProductCategoriesApiService);
  private readonly packagesApi = inject(CatalogPackagesApiService);
  readonly workspaces = inject(WorkspaceService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searchInput$ = new Subject<string>();

  readonly palette = CATEGORY_COLOR_PALETTE;
  readonly billingOptions = BILLING_TYPE_OPTIONS;

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly items = signal<Product[]>([]);
  readonly page = new PageState();
  readonly productStats = signal<ProductStats>(EMPTY_PRODUCT_STATS);
  readonly categories = signal<ProductCategory[]>([]);
  readonly deletingId = signal<string | null>(null);
  readonly duplicatingId = signal<string | null>(null);
  readonly activeTab = signal<CatalogTab>('products');
  readonly categoryColorMap = signal<Record<string, string>>(loadCategoryColorMap());
  readonly packages = signal<CatalogPackage[]>([]);
  readonly packagesLoading = signal(false);
  readonly deletingPackageId = signal<string | null>(null);
  readonly showPackageModal = signal(false);
  readonly editingPackageId = signal<string | null>(null);
  readonly packageFormItems = signal<CatalogPackageItemPayload[]>([]);
  /** Produits référencés par le package en cours d'édition (indépendant de la page affichée). */
  private readonly packageProducts = signal<ReadonlyMap<string, Product>>(new Map());
  readonly productPicker = productPickerSource(this.api, this.workspaces);

  readonly filterQ = signal('');
  readonly filterCategoryId = signal('');
  readonly filterActiveOnly = signal(true);

  readonly editingCatId = signal<string | null>(null);
  readonly inlineCatLabel = signal('');
  readonly showCategoryModal = signal(false);
  readonly categoryModalColorHex = signal(CATEGORY_COLOR_PALETTE[0].hex);
  readonly deletingCategoryId = signal<string | null>(null);

  readonly categoryForm = this.fb.nonNullable.group({
    code: ['', Validators.required],
    label: ['', Validators.required],
    sortOrder: [0, [Validators.required, Validators.min(0)]],
  });

  readonly packageForm = this.fb.nonNullable.group({
    referenceCode: ['', Validators.required],
    title: ['', Validators.required],
    description: [''],
    discountType: ['PERCENT' as PackageDiscountType, Validators.required],
    discountValue: [0, [Validators.required, Validators.min(0)]],
    badgeTheme: ['purple'],
    active: [true],
  });

  readonly editingCategoryId = signal<string | null>(null);

  readonly productSortKey = signal<ProductSortKey>('referenceCode');
  readonly productSortDir = signal<SortDirection>('asc');
  readonly categorySortKey = signal<CategorySortKey>('sortOrder');
  readonly categorySortDir = signal<SortDirection>('asc');

  readonly editingProductId = signal<string | null>(null);
  readonly productEditDraft = signal<ProductInlineDraft | null>(null);
  readonly savingProductId = signal<string | null>(null);
  readonly inlineCatCode = signal('');
  readonly inlineCatSortOrder = signal(0);

  /** KPI globaux (serveur) — indépendants des filtres et de la page affichée. */
  readonly stats = computed(() => {
    const s = this.productStats();
    return {
      totalProds: s.activeProducts,
      totalPkgs: this.packages().length,
      totalCats: this.categories().length,
      avgTjm: Math.round(s.averageDayRateHt),
      totalCatalogValue: s.catalogValueHt,
    };
  });

  readonly sortedCategories = computed(() => {
    const list = [...this.categories()];
    const key = this.categorySortKey();
    const mul = sortMultiplier(this.categorySortDir());
    list.sort((a, b) => {
      let cmp = 0;
      switch (key) {
        case 'code':
          cmp = a.code.localeCompare(b.code, 'fr');
          break;
        case 'label':
          cmp = a.label.localeCompare(b.label, 'fr');
          break;
        case 'sortOrder':
          cmp = a.sortOrder - b.sortOrder;
          break;
        case 'products':
          cmp = this.productCountForCategory(a.id) - this.productCountForCategory(b.id);
          break;
      }
      return cmp * mul;
    });
    return list;
  });

  constructor() {
    this.searchInput$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((q) => {
        this.filterQ.set(q);
        this.page.reset();
        this.reloadProducts();
      });

    effect(() => {
      this.workspaces.generation();
      this.page.reset();
      this.loadCategories();
      this.reloadProducts();
      this.reloadPackages();
    });
  }

  ngOnInit(): void {
    const tab = this.route.snapshot.data['catalogTab'] as CatalogTab | undefined;
    if (tab === 'categories' || tab === 'packages') {
      this.activeTab.set(tab);
    }
  }

  setTab(tab: CatalogTab): void {
    this.activeTab.set(tab);
  }

  onProductSort(key: string): void {
    const k = key as ProductSortKey;
    this.productSortDir.set(nextSortDirection(this.productSortKey(), k, this.productSortDir()));
    this.productSortKey.set(k);
    this.page.reset();
    this.reloadProducts();
  }

  onPageChange(index: number): void {
    this.page.goTo(index);
    this.reloadProducts();
  }

  onPageSizeChange(size: number): void {
    this.page.setSize(size);
    this.reloadProducts();
  }

  onCategorySort(key: string): void {
    const k = key as CategorySortKey;
    this.categorySortDir.set(nextSortDirection(this.categorySortKey(), k, this.categorySortDir()));
    this.categorySortKey.set(k);
  }

  startProductInlineEdit(p: Product): void {
    this.editingProductId.set(p.id);
    this.productEditDraft.set({
      name: p.name,
      categoryId: p.categoryId ?? '',
      billingType: p.billingType,
      unitLabel: p.unitLabel === defaultUnitLabel(p.billingType) ? '' : p.unitLabel,
      unitPriceHt: p.unitPriceHt,
      vatRate: p.vatRate,
    });
  }

  /** Changement de mode : l'unité personnalisée est réinitialisée vers le défaut du nouveau mode. */
  patchDraftBillingType(type: BillingType): void {
    this.patchProductDraft({ billingType: type, unitLabel: '' });
  }

  cancelProductInlineEdit(): void {
    this.editingProductId.set(null);
    this.productEditDraft.set(null);
  }

  patchProductDraft(partial: Partial<ProductInlineDraft>): void {
    const cur = this.productEditDraft();
    if (cur) {
      this.productEditDraft.set({ ...cur, ...partial });
    }
  }

  saveProductInlineEdit(p: Product): void {
    const draft = this.productEditDraft();
    if (!draft?.name.trim()) {
      return;
    }
    const workspaceId = this.workspaces.requireSelectedId();
    this.savingProductId.set(p.id);
    this.api
      .update(p.id, {
        workspaceId,
        referenceCode: p.referenceCode,
        name: draft.name.trim(),
        description: p.description,
        categoryId: draft.categoryId || null,
        unitPriceHt: draft.unitPriceHt,
        vatRate: draft.vatRate,
        active: p.active,
        billingType: draft.billingType,
        unitLabel: draft.unitLabel.trim() || null,
      })
      .subscribe({
        next: () => {
          this.savingProductId.set(null);
          this.cancelProductInlineEdit();
          this.reloadProducts();
        },
        error: (err) => {
          this.savingProductId.set(null);
          this.error.set(apiErrorMessage(err, 'Mise à jour impossible'));
        },
      });
  }

  badgeClasses(theme: string): string {
    return packageBadgeClasses(theme);
  }

  productById(id: string): Product | undefined {
    return this.items().find((p) => p.id === id);
  }

  onSearchInput(value: string): void {
    this.searchInput$.next(value);
  }

  clearSearch(): void {
    this.filterQ.set('');
    this.searchInput$.next('');
  }

  onCategoryFilterChange(value: string): void {
    this.filterCategoryId.set(value);
    this.page.reset();
    this.reloadProducts();
  }

  onActiveOnlyChange(checked: boolean): void {
    this.filterActiveOnly.set(checked);
    this.page.reset();
    this.reloadProducts();
  }

  loadCategories(): void {
    const workspaceId = this.workspaces.selectedId();
    if (!workspaceId) {
      return;
    }
    this.categoriesApi.list(workspaceId).subscribe({
      next: (list) => this.categories.set(list),
      error: () => this.categories.set([]),
    });
  }

  reloadPackages(): void {
    const workspaceId = this.workspaces.selectedId();
    if (!workspaceId) {
      return;
    }
    const gen = this.workspaces.generation();
    this.packagesLoading.set(true);
    this.packagesApi.list(workspaceId).subscribe({
      next: (list) => {
        if (this.workspaces.isStale(gen)) {
          return;
        }
        this.packages.set(list);
        this.packagesLoading.set(false);
      },
      error: () => {
        if (this.workspaces.isStale(gen)) {
          return;
        }
        this.packages.set([]);
        this.packagesLoading.set(false);
      },
    });
  }

  /** Liste paginée + KPI globaux (les KPI dépendent des produits, pas des filtres). */
  reloadProducts(): void {
    const workspaceId = this.workspaces.selectedId();
    if (!workspaceId) {
      return;
    }
    const gen = this.workspaces.generation();
    this.loading.set(true);
    this.api.stats(workspaceId).subscribe({
      next: (stats) => {
        if (!this.workspaces.isStale(gen)) {
          this.productStats.set(stats);
        }
      },
      error: () => {
        if (!this.workspaces.isStale(gen)) {
          this.productStats.set(EMPTY_PRODUCT_STATS);
        }
      },
    });
    const sort = sortParam(PRODUCT_SORT_PROPERTIES[this.productSortKey()], this.productSortDir());
    this.api
      .search(workspaceId, {
        q: this.filterQ().trim() || undefined,
        activeOnly: this.filterActiveOnly(),
        categoryId: this.filterCategoryId() || undefined,
        ...this.page.query(sort),
      })
      .subscribe({
      next: (page) => {
        if (this.workspaces.isStale(gen)) {
          return;
        }
        this.items.set(page.content);
        this.page.apply(page);
        this.loading.set(false);
      },
      error: (err) => {
        if (this.workspaces.isStale(gen)) {
          return;
        }
        this.error.set(apiErrorMessage(err, 'Erreur'));
        this.loading.set(false);
      },
    });
  }

  categoryById(id: string | null | undefined): ProductCategory | undefined {
    if (!id) {
      return undefined;
    }
    return this.categories().find((c) => c.id === id);
  }

  categoryColor(category: ProductCategory | undefined): CategoryColorTheme {
    return resolveCategoryColor(category, this.categoryColorMap());
  }

  pickCategoryColor(categoryId: string, theme: CategoryColorTheme): void {
    saveCategoryColor(categoryId, theme.hex);
    this.categoryColorMap.set(loadCategoryColorMap());
  }

  shortDescription(product: Product): string {
    return productShortDescription(product.description);
  }

  draftUnitPlaceholder(type: BillingType): string {
    return defaultUnitLabel(type);
  }

  priceFormatted(value: number): string {
    return formatPriceHt(value);
  }

  productCountForCategory(categoryId: string): number {
    return this.productStats().productsByCategory[categoryId] ?? 0;
  }

  openNewCategoryModal(): void {
    this.editingCategoryId.set(null);
    this.categoryForm.reset({ code: '', label: '', sortOrder: this.categories().length + 1 });
    this.categoryModalColorHex.set(CATEGORY_COLOR_PALETTE[0].hex);
    this.showCategoryModal.set(true);
  }

  closeCategoryModal(): void {
    this.showCategoryModal.set(false);
  }

  saveCategoryModal(): void {
    if (this.categoryForm.invalid || this.saving()) {
      return;
    }
    const workspaceId = this.workspaces.requireSelectedId();
    const payload = { workspaceId, ...this.categoryForm.getRawValue() };
    const id = this.editingCategoryId();
    this.saving.set(true);
    const req = id ? this.categoriesApi.update(id, payload) : this.categoriesApi.create(payload);
    req.subscribe({
      next: (cat) => {
        saveCategoryColor(cat.id, this.categoryModalColorHex());
        this.categoryColorMap.set(loadCategoryColorMap());
        this.saving.set(false);
        this.closeCategoryModal();
        this.loadCategories();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Enregistrement catégorie impossible'));
      },
    });
  }

  startInlineEditCategory(cat: ProductCategory): void {
    this.editingCatId.set(cat.id);
    this.inlineCatLabel.set(cat.label);
    this.inlineCatCode.set(cat.code);
    this.inlineCatSortOrder.set(cat.sortOrder);
  }

  cancelInlineEditCategory(): void {
    this.editingCatId.set(null);
  }

  saveInlineEditCategory(cat: ProductCategory): void {
    const label = this.inlineCatLabel().trim();
    const code = this.inlineCatCode().trim();
    if (!label || !code) {
      return;
    }
    const workspaceId = this.workspaces.requireSelectedId();
    this.categoriesApi
      .update(cat.id, { workspaceId, code, label, sortOrder: this.inlineCatSortOrder() })
      .subscribe({
      next: () => {
        this.editingCatId.set(null);
        this.loadCategories();
        this.reloadProducts();
      },
      error: (err) => this.error.set(apiErrorMessage(err, 'Mise à jour impossible')),
    });
  }

  confirmDeleteCategory(cat: ProductCategory): void {
    const ok = confirm(`Supprimer la catégorie « ${cat.label} » ?\n\nImpossible si des produits y sont rattachés.`);
    if (!ok) {
      return;
    }
    this.deletingCategoryId.set(cat.id);
    this.categoriesApi.delete(cat.id).subscribe({
      next: () => {
        this.deletingCategoryId.set(null);
        this.loadCategories();
      },
      error: (err) => {
        this.deletingCategoryId.set(null);
        this.error.set(apiErrorMessage(err, 'Suppression impossible'));
      },
    });
  }

  confirmDelete(p: Product): void {
    const ok = confirm(
      `Supprimer le produit « ${p.name} » (${p.referenceCode}) ?\n\nImpossible s'il est utilisé sur un devis ou un contrat.`,
    );
    if (!ok) {
      return;
    }
    this.error.set(null);
    this.deletingId.set(p.id);
    this.api.delete(p.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.page.adjustAfterRemoval();
        this.reloadProducts();
      },
      error: (err) => {
        this.deletingId.set(null);
        this.error.set(apiErrorMessage(err, 'Suppression impossible'));
      },
    });
  }

  openPackageModal(pkg?: CatalogPackage): void {
    this.packageProducts.set(new Map());
    if (pkg) {
      this.editingPackageId.set(pkg.id);
      this.packageForm.patchValue({
        referenceCode: pkg.referenceCode,
        title: pkg.title,
        description: pkg.description ?? '',
        discountType: pkg.discountType,
        discountValue: pkg.discountValue,
        badgeTheme: pkg.badgeTheme,
        active: pkg.active,
      });
      this.packageFormItems.set(pkg.items.map((i) => ({ productId: i.productId, quantity: i.quantity })));
      this.loadPackageProducts(pkg.items.map((i) => i.productId));
    } else {
      this.editingPackageId.set(null);
      this.packageForm.reset({
        referenceCode: `PKG-${Math.floor(1000 + Math.random() * 9000)}`,
        title: '',
        description: '',
        discountType: 'PERCENT',
        discountValue: 5,
        badgeTheme: 'purple',
        active: true,
      });
      this.packageFormItems.set([]);
    }
    this.showPackageModal.set(true);
    this.setTab('packages');
  }

  closePackageModal(): void {
    this.showPackageModal.set(false);
  }

  onPackageProductPicked(product: Product | null): void {
    if (!product) {
      return;
    }
    this.rememberPackageProducts([product]);
    this.addProductToPackageForm(product.id);
  }

  addProductToPackageForm(productId: string): void {
    if (!productId || this.packageFormItems().some((i) => i.productId === productId)) {
      return;
    }
    this.packageFormItems.update((items) => [...items, { productId, quantity: 1 }]);
  }

  packageProduct(productId: string): Product | undefined {
    return this.packageProducts().get(productId) ?? this.productById(productId);
  }

  private rememberPackageProducts(list: readonly Product[]): void {
    this.packageProducts.update((map) => {
      const next = new Map(map);
      for (const p of list) {
        next.set(p.id, p);
      }
      return next;
    });
  }

  private loadPackageProducts(ids: readonly string[]): void {
    const missing = ids.filter((id) => !this.packageProduct(id));
    if (!missing.length) {
      return;
    }
    forkJoin(missing.map((id) => this.api.get(id).pipe(catchError(() => of(null))))).subscribe((list) =>
      this.rememberPackageProducts(list.filter((p): p is Product => !!p)),
    );
  }

  removeProductFromPackageForm(productId: string): void {
    this.packageFormItems.update((items) => items.filter((i) => i.productId !== productId));
  }

  updatePackageItemQty(productId: string, qty: number): void {
    const quantity = Math.max(0.0001, qty || 1);
    this.packageFormItems.update((items) =>
      items.map((i) => (i.productId === productId ? { ...i, quantity } : i)),
    );
  }

  setPackageDiscountType(type: PackageDiscountType): void {
    this.packageForm.patchValue({ discountType: type });
  }

  savePackageModal(): void {
    if (this.packageForm.invalid || this.saving() || !this.packageFormItems().length) {
      return;
    }
    const workspaceId = this.workspaces.requireSelectedId();
    const raw = this.packageForm.getRawValue();
    const payload = {
      workspaceId,
      referenceCode: raw.referenceCode,
      title: raw.title,
      description: raw.description || null,
      discountType: raw.discountType,
      discountValue: raw.discountValue,
      badgeTheme: raw.badgeTheme,
      active: raw.active,
      items: this.packageFormItems(),
    };
    const id = this.editingPackageId();
    this.saving.set(true);
    const req = id ? this.packagesApi.update(id, payload) : this.packagesApi.create(payload);
    req.subscribe({
      next: () => {
        this.saving.set(false);
        this.closePackageModal();
        this.reloadPackages();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Enregistrement package impossible'));
      },
    });
  }

  confirmDeletePackage(pkg: CatalogPackage): void {
    const ok = confirm(`Supprimer le package « ${pkg.title} » (${pkg.referenceCode}) ?`);
    if (!ok) {
      return;
    }
    this.deletingPackageId.set(pkg.id);
    this.packagesApi.delete(pkg.id).subscribe({
      next: () => {
        this.deletingPackageId.set(null);
        this.reloadPackages();
      },
      error: (err) => {
        this.deletingPackageId.set(null);
        this.error.set(apiErrorMessage(err, 'Suppression impossible'));
      },
    });
  }

  packageLineTotal(productId: string, quantity: number): number {
    const p = this.packageProduct(productId);
    return p ? p.unitPriceHt * quantity : 0;
  }

  livePackageTotals(): { grossTotal: number; discountAmount: number; netTotal: number } {
    const raw = this.packageForm.getRawValue();
    return this.computePackageTotals(
      this.packageFormItems(),
      raw.discountType,
      Number(raw.discountValue) || 0,
    );
  }

  private computePackageTotals(
    items: CatalogPackageItemPayload[],
    discountType: PackageDiscountType,
    discountValue: number,
  ): { grossTotal: number; discountAmount: number; netTotal: number } {
    let grossTotal = 0;
    for (const item of items) {
      grossTotal += this.packageLineTotal(item.productId, item.quantity);
    }
    let discountAmount = 0;
    if (discountValue > 0) {
      discountAmount = discountType === 'PERCENT' ? (grossTotal * discountValue) / 100 : discountValue;
    }
    discountAmount = Math.min(discountAmount, grossTotal);
    return { grossTotal, discountAmount, netTotal: Math.max(0, grossTotal - discountAmount) };
  }

  duplicateProduct(p: Product): void {
    const workspaceId = this.workspaces.requireSelectedId();
    this.duplicatingId.set(p.id);
    this.api
      .create({
        workspaceId,
        referenceCode: `${p.referenceCode}-COPY`,
        name: `${p.name} (Copie)`,
        description: p.description,
        categoryId: p.categoryId ?? null,
        unitPriceHt: p.unitPriceHt,
        vatRate: p.vatRate,
        active: p.active,
        billingType: p.billingType,
        unitLabel: p.unitLabel,
      })
      .subscribe({
        next: () => {
          this.duplicatingId.set(null);
          this.reloadProducts();
        },
        error: (err) => {
          this.duplicatingId.set(null);
          this.error.set(apiErrorMessage(err, 'Duplication impossible'));
        },
      });
  }
}
