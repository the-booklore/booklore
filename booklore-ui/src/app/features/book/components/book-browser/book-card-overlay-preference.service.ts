import {inject, Injectable} from '@angular/core';
import {BehaviorSubject, Subject} from 'rxjs';
import {debounceTime, filter, takeUntil} from 'rxjs/operators';
import {UserService} from '../../../settings/user-management/user.service';

@Injectable({
  providedIn: 'root'
})
export class BookCardOverlayPreferenceService {
  private readonly userService = inject(UserService);

  private readonly _showBookTypePill = new BehaviorSubject<boolean>(true);
  readonly showBookTypePill$ = this._showBookTypePill.asObservable();

  private destroy$ = new Subject<void>();
  private hasUserToggled = false;
  private currentContext: { type: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF', id: number } | null = null;

  constructor() {
    this.userService.userState$
      .pipe(
        filter(userState => !!userState?.user && userState.loaded),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        this.loadPreferencesFromUser();
      });

    this.showBookTypePill$
      .pipe(debounceTime(500))
      .subscribe(show => {
        if (this.hasUserToggled) {
          this.persistPreference(show);
        }
      });
  }

  setShowBookTypePill(show: boolean): void {
    this.hasUserToggled = true;
    this._showBookTypePill.next(show);
  }

  get showBookTypePill(): boolean {
    return this._showBookTypePill.value;
  }

  private loadPreferencesFromUser(): void {
    const user = this.userService.getCurrentUser();
    const prefs = user?.userSettings?.entityViewPreferences;

    let show = true;
    if (prefs) {
      const globalAny = prefs.global as any;
      show = prefs.global?.overlayBookType ?? globalAny?.showBookTypePill ?? true;

      if (this.currentContext) {
        const override = prefs.overrides?.find(o =>
          o.entityType === this.currentContext?.type && o.entityId === this.currentContext?.id
        );
        if (override) {
          const prefAny = override.preferences as any;
          if (override.preferences.overlayBookType !== undefined) {
            show = override.preferences.overlayBookType;
          } else if (prefAny?.showBookTypePill !== undefined) {
            show = prefAny.showBookTypePill;
          }
        }
      }
    }

    this.hasUserToggled = false;
    if (this._showBookTypePill.value !== show) {
      this._showBookTypePill.next(show);
    }
  }

  private persistPreference(show: boolean): void {
    const user = this.userService.getCurrentUser();
    if (!user) return;

    const prefs = structuredClone(user.userSettings.entityViewPreferences ?? {
      global: {
        sortKey: 'addedOn',
        sortDir: 'DESC',
        view: 'GRID',
        coverSize: 1.0,
        seriesCollapsed: false,
        overlayBookType: true
      },
      overrides: []
    });

    if (!prefs.overrides) {
      prefs.overrides = [];
    }

    if (this.currentContext) {
      let override = prefs.overrides.find(o =>
        o.entityType === this.currentContext?.type && o.entityId === this.currentContext?.id
      );

      if (!override) {
        override = {
          entityType: this.currentContext.type,
          entityId: this.currentContext.id,
          preferences: {
            ...prefs.global,
            overlayBookType: show
          }
        };
        prefs.overrides.push(override);
      } else {
        override.preferences.overlayBookType = show;
      }
    } else {
      prefs.global.overlayBookType = show;
    }

    this.userService.updateUserSetting(user.id, 'entityViewPreferences', prefs);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
