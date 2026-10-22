import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { WorkspaceService } from '../../core/workspace/workspace.service';
import { CustomerPayload, CustomerStatus, CustomerType } from '../../shared/models/customer.model';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { CustomersApiService } from './customers-api.service';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

@Component({
  selector: 'app-customers-form-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ErrorBannerComponent, LoadingStateComponent],
  template: `
    <div class="mx-auto max-w-2xl space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-white">{{ editId ? 'Modifier le client' : 'Nouveau client' }}</h1>
        <a routerLink="/customers" class="text-sm text-slate-400 hover:text-white">Retour</a>
      </div>

      @if (error()) {
        <app-error-banner [message]="error()!" />
      }
      @if (loading()) {
        <app-loading-state />
      } @else {
        <form class="space-y-4 rounded-xl border border-slate-800 bg-slate-900/50 p-6" [formGroup]="form" (ngSubmit)="save()">
          <div class="grid gap-4 sm:grid-cols-2">
            <div>
              <label class="mb-1 block text-xs text-slate-500">Type</label>
              <select formControlName="customerType" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white">
                <option value="COMPANY">Entreprise</option>
                <option value="INDIVIDUAL">Particulier</option>
              </select>
            </div>
            <div>
              <label class="mb-1 block text-xs text-slate-500">Statut</label>
              <select formControlName="status" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white">
                <option value="PROSPECT">Prospect</option>
                <option value="CLIENT">Client</option>
                <option value="INACTIVE">Inactif</option>
              </select>
            </div>
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">Raison sociale</label>
            <input formControlName="companyName" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
          </div>
          <div class="grid gap-4 sm:grid-cols-2">
            <div>
              <label class="mb-1 block text-xs text-slate-500">Prénom</label>
              <input formControlName="firstName" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
            </div>
            <div>
              <label class="mb-1 block text-xs text-slate-500">Nom</label>
              <input formControlName="lastName" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
            </div>
          </div>
          <div class="grid gap-4 sm:grid-cols-2">
            <div>
              <label class="mb-1 block text-xs text-slate-500">E-mail</label>
              <input type="email" formControlName="email" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
            </div>
            <div>
              <label class="mb-1 block text-xs text-slate-500">Téléphone</label>
              <input formControlName="phone" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
            </div>
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">Adresse</label>
            <input formControlName="addressLine1" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
          </div>
          <div class="grid gap-4 sm:grid-cols-3">
            <div>
              <label class="mb-1 block text-xs text-slate-500">Code postal</label>
              <input formControlName="postalCode" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
            </div>
            <div>
              <label class="mb-1 block text-xs text-slate-500">Ville</label>
              <input formControlName="city" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
            </div>
            <div>
              <label class="mb-1 block text-xs text-slate-500">Pays (ISO)</label>
              <input formControlName="countryCode" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white" />
            </div>
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">Notes</label>
            <textarea formControlName="notes" rows="3" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"></textarea>
          </div>
          <button
            type="submit"
            class="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500 disabled:opacity-50"
            [disabled]="form.invalid || saving()"
          >
            Enregistrer
          </button>
        </form>
      }
    </div>
  `,
})
export class CustomersFormPageComponent implements OnInit {
  private readonly api = inject(CustomersApiService);
  private readonly workspaces = inject(WorkspaceService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  editId: string | null = null;
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    customerType: ['COMPANY' as CustomerType, Validators.required],
    companyName: [''],
    firstName: [''],
    lastName: [''],
    email: [''],
    phone: [''],
    addressLine1: [''],
    addressLine2: [''],
    postalCode: [''],
    city: [''],
    countryCode: ['FR', Validators.required],
    status: ['PROSPECT' as CustomerStatus, Validators.required],
    notes: [''],
    nextFollowUpAt: [null as string | null],
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id && this.route.snapshot.url.some((s) => s.path === 'edit')) {
      this.editId = id;
      this.loading.set(true);
      this.api.get(id).subscribe({
        next: (c) => {
          this.form.patchValue({
            customerType: c.customerType,
            companyName: c.companyName ?? '',
            firstName: c.firstName ?? '',
            lastName: c.lastName ?? '',
            email: c.email ?? '',
            phone: c.phone ?? '',
            addressLine1: c.addressLine1 ?? '',
            addressLine2: c.addressLine2 ?? '',
            postalCode: c.postalCode ?? '',
            city: c.city ?? '',
            countryCode: c.countryCode,
            status: c.status,
            notes: c.notes ?? '',
            nextFollowUpAt: c.nextFollowUpAt,
          });
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(apiErrorMessage(err, 'Client introuvable'));
          this.loading.set(false);
        },
      });
    }
  }

  save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    const workspaceId = this.workspaces.requireSelectedId();
    const payload: CustomerPayload = {
      workspaceId,
      ...this.form.getRawValue(),
      companyName: this.form.value.companyName || null,
      firstName: this.form.value.firstName || null,
      lastName: this.form.value.lastName || null,
      email: this.form.value.email || null,
      phone: this.form.value.phone || null,
      addressLine1: this.form.value.addressLine1 || null,
      addressLine2: this.form.value.addressLine2 || null,
      postalCode: this.form.value.postalCode || null,
      city: this.form.value.city || null,
      notes: this.form.value.notes || null,
    };
    this.saving.set(true);
    this.error.set(null);
    const req = this.editId ? this.api.update(this.editId, payload) : this.api.create(payload);
    req.subscribe({
      next: (c) => void this.router.navigate(['/customers', c.id]),
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Enregistrement impossible'));
        this.saving.set(false);
      },
      complete: () => this.saving.set(false),
    });
  }
}
