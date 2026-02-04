import {inject, Injectable, OnDestroy} from '@angular/core';
import {Subject} from 'rxjs';
import {debounceTime, distinctUntilChanged, filter, map, takeUntil} from 'rxjs/operators';
import {MessageService} from 'primeng/api';
import {LocalStorageService} from '../../../../shared/service/local-storage.service';
import {Book} from '../../model/book.model';
import {EntityViewPreferences, User, UserService, UserState} from '../../../settings/user-management/user.service';

@Injectable({
  providedIn: 'root'
})
export class CoverScalePreferenceService implements OnDestroy {

  private readonly BASE_WIDTH = 135;
  private readonly BASE_HEIGHT = 220;
  private readonly TITLE_BAR_HEIGHT = 31;
  private readonly DEBOUNCE_MS = 1000;
  private readonly STORAGE_KEY = 'coverScalePreference';

  private readonly messageService = inject(MessageService);
  private readonly localStorageService = inject(LocalStorageService);
  private readonly userService = inject(UserService);

  private readonly destroy$ = new Subject<void>();
  private readonly scaleChangeSubject = new Subject<number>();
  readonly scaleChange$ = this.scaleChangeSubject.asObservable();

  private isUpdating = false;
  scaleFactor = 1.0;

  constructor() {
    this.userService.userState$.pipe(
      filter((u: any): u is UserState => !!u && u.loaded && !!u.user),
      map(u => (u.user?.userSettings?.entityViewPreferences as EntityViewPreferences)?.global?.coverSize),
      filter((size): size is number => typeof size === 'number' && size > 0),
      distinctUntilChanged(),
      filter(() => !this.isUpdating),
      takeUntil(this.destroy$)
    ).subscribe((size: number) => {
      this.scaleFactor = size;
      this.scaleChangeSubject.next(size);
    });

    this.scaleChange$
      .pipe(
        debounceTime(this.DEBOUNCE_MS),
        filter(scale => {
          const user = this.userService.getCurrentUser() as User | null;
          const persistedScale = (user?.userSettings?.entityViewPreferences as EntityViewPreferences)?.global?.coverSize;
          return typeof scale === 'number' && scale !== persistedScale;
        }),
        takeUntil(this.destroy$)
      )
      .subscribe(scale => this.saveScalePreference(scale));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  initScaleValue(scale: number | undefined): void {
    this.scaleFactor = scale ?? 1.0;
  }

  setScale(scale: number): void {
    this.scaleFactor = scale;
    this.scaleChangeSubject.next(scale);
  }

  get currentCardSize(): { width: number; height: number } {
    return {
      width: Math.round(this.BASE_WIDTH * this.scaleFactor),
      height: Math.round(this.BASE_HEIGHT * this.scaleFactor),
    };
  }

  get gridColumnMinWidth(): string {
    return `${this.currentCardSize.width}px`;
  }

  getCardHeight(book: Book): number {
    const isAudiobook = book.primaryFile?.bookType === 'AUDIOBOOK';
    if (isAudiobook) {
      return Math.round((this.BASE_WIDTH + this.TITLE_BAR_HEIGHT) * this.scaleFactor);
    }
    return this.currentCardSize.height;
  }

  private saveScalePreference(scale: number): void {
    this.isUpdating = true;
    try {
      this.localStorageService.set(this.STORAGE_KEY, scale);
      const user = this.userService.getCurrentUser() as User | null;
      if (user) {
        const currentPrefs = (user.userSettings?.entityViewPreferences as EntityViewPreferences) || {global: {}, overrides: []};
        const newPrefs: EntityViewPreferences = {
          ...currentPrefs,
          global: {
            ...currentPrefs.global,
            coverSize: scale
          }
        };
        this.userService.updateUserSetting(user.id, 'entityViewPreferences', newPrefs);
      }

      this.messageService.add({
        severity: 'success',
        summary: 'Cover Size Saved',
        detail: `Cover size set to ${scale.toFixed(2)}x.`,
        life: 1500
      });
    } catch (e) {
      this.messageService.add({
        severity: 'error',
        summary: 'Save Failed',
        detail: 'Could not save cover size preference.',
        life: 3000
      });
    } finally {
      // Small delay to let userState$ settle before re-enabling sync
      setTimeout(() => this.isUpdating = false, 100);
    }
  }

  private loadScaleFromStorage(): void {
    const saved = this.localStorageService.get<number>(this.STORAGE_KEY);
    if (saved !== null && !isNaN(saved)) {
      this.scaleFactor = saved;
    }
  }
}
