import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { moduleLabel, MODULES } from './module.service';

@Component({
  selector: 'app-module-disabled-page',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-xl rounded-xl border border-slate-800 bg-slate-900/60 p-8 text-center">
      <p class="text-xs font-semibold uppercase tracking-wider text-orange-400">Module non activé</p>
      <h1 class="mt-2 text-xl font-semibold text-white">{{ label() }}</h1>
      <p class="mt-3 text-sm text-slate-400">
        {{ description() }}
      </p>
      <p class="mt-4 text-sm text-slate-500">
        Ce module ne fait pas partie de votre abonnement actuel. Contactez votre éditeur pour l’activer.
      </p>
      <a
        routerLink="/dashboard"
        class="mt-6 inline-flex rounded-lg bg-slate-800 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
      >
        Retour au tableau de bord
      </a>
    </div>
  `,
})
export class ModuleDisabledPageComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly code = toSignal(this.route.queryParamMap.pipe(map((p) => p.get('module') ?? '')), {
    initialValue: '',
  });

  readonly label = computed(() => (this.code() ? moduleLabel(this.code()) : 'Module'));
  readonly description = computed(
    () => MODULES.find((m) => m.code === this.code())?.description ?? 'Cette fonctionnalité est optionnelle.',
  );
}
