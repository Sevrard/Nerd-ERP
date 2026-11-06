import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiClient } from '../api/api-client.service';
import { WorkspaceService } from './workspace.service';
import { Workspace } from '../../shared/models/workspace.model';

describe('WorkspaceService', () => {
  const baseWs = (over: Partial<Workspace> & Pick<Workspace, 'id' | 'name' | 'slug'>): Workspace => ({
    organizationId: 'org-1',
    currencyCode: 'EUR',
    brandColor: null,
    logoUrl: null,
    active: true,
    legalSubtitle: null,
    addressLine1: null,
    addressLine2: null,
    postalCode: null,
    city: null,
    region: null,
    countryCode: 'FR',
    contactEmail: null,
    websiteUrl: null,
    siren: null,
    vatNumber: null,
    iban: null,
    bic: null,
    invoicePrefix: 'FAC',
    quotationPrefix: 'DEV',
    ...over,
  });
  const wsA = baseWs({ id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', name: 'Alpha', slug: 'alpha' });
  const wsB = baseWs({ id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', name: 'Beta', slug: 'beta' });

  let service: WorkspaceService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        WorkspaceService,
        {
          provide: ApiClient,
          useValue: {
            get: vi.fn().mockReturnValue(of([wsA, wsB])),
          },
        },
      ],
    });
    service = TestBed.inject(WorkspaceService);
  });

  it('selectWorkspace bumps generation and persists id', () => {
    service.loadWorkspaces().subscribe();
    const gen0 = service.generation();
    service.selectWorkspace(wsB.id);
    expect(service.selectedId()).toBe(wsB.id);
    expect(service.generation()).toBe(gen0 + 1);
    expect(localStorage.getItem('nerd-erp.workspace-id')).toBe(wsB.id);
  });

  it('isStale detects generation drift', () => {
    const gen = service.generation();
    service.selectWorkspace(wsA.id);
    expect(service.isStale(gen)).toBe(true);
    expect(service.isStale(service.generation())).toBe(false);
  });

  it('selectWorkspace with same id is a no-op', () => {
    service.selectWorkspace(wsA.id, false);
    const gen = service.generation();
    service.selectWorkspace(wsA.id);
    expect(service.generation()).toBe(gen);
  });
});
