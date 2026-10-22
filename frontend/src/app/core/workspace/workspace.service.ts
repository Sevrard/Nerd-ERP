import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { ApiClient } from '../api/api-client.service';
import { Workspace } from '../../shared/models/workspace.model';

const STORAGE_KEY = 'nerd-erp.workspace-id';

@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly api = inject(ApiClient);

  private readonly workspacesSignal = signal<Workspace[]>([]);
  private readonly selectedIdSignal = signal<string | null>(this.readStoredId());
  /** Incrémenté à chaque changement de workspace pour invalider les réponses tardives. */
  private readonly generationSignal = signal(0);

  readonly workspaces = this.workspacesSignal.asReadonly();
  readonly selectedId = this.selectedIdSignal.asReadonly();
  readonly generation = this.generationSignal.asReadonly();

  readonly selectedWorkspace = computed(() => {
    const id = this.selectedIdSignal();
    return this.workspacesSignal().find((w) => w.id === id) ?? null;
  });

  loadWorkspaces(): Observable<Workspace[]> {
    return this.api.get<Workspace[]>('/workspaces').pipe(
      tap((list) => {
        this.applyWorkspaceList(list);
      }),
    );
  }

  createWorkspace(payload: { name: string; slug?: string }): Observable<Workspace> {
    return this.api.post<Workspace>('/workspaces', payload).pipe(
      tap((created) => {
        this.workspacesSignal.update((list) => [...list, created].sort((a, b) => a.name.localeCompare(b.name)));
        this.selectWorkspace(created.id);
      }),
    );
  }

  deleteWorkspace(id: string): Observable<void> {
    return this.api.delete(`/workspaces/${id}`).pipe(
      tap(() => {
        const remaining = this.workspacesSignal().filter((w) => w.id !== id);
        this.applyWorkspaceList(remaining);
      }),
    );
  }

  private applyWorkspaceList(list: Workspace[]): void {
    this.workspacesSignal.set(list);
    const current = this.selectedIdSignal();
    const exists = current && list.some((w) => w.id === current);
    if (!exists && list.length) {
      this.selectWorkspace(list[0].id, false);
    }
    if (!list.length) {
      this.selectedIdSignal.set(null);
      localStorage.removeItem(STORAGE_KEY);
    }
  }

  selectWorkspace(id: string, bumpGeneration = true): void {
    if (this.selectedIdSignal() === id) {
      return;
    }
    this.selectedIdSignal.set(id);
    localStorage.setItem(STORAGE_KEY, id);
    if (bumpGeneration) {
      this.generationSignal.update((g) => g + 1);
    }
  }

  /** À appeler au début d’un chargement lié au workspace ; comparer avec generation() à la réception. */
  isStale(generationAtStart: number): boolean {
    return generationAtStart !== this.generationSignal();
  }

  requireSelectedId(): string {
    const id = this.selectedIdSignal();
    if (!id) {
      throw new Error('Aucun workspace sélectionné');
    }
    return id;
  }

  private readStoredId(): string | null {
    return localStorage.getItem(STORAGE_KEY);
  }
}
