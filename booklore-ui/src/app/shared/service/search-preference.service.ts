import {inject, Injectable} from '@angular/core';
import {BehaviorSubject, Observable} from 'rxjs';
import {LocalStorageService} from './local-storage.service';

export type SearchTriggerMode = 'instant' | 'button';

const VALID_MODES: SearchTriggerMode[] = ['instant', 'button'];
const STORAGE_KEY = 'searchTriggerMode';

@Injectable({ providedIn: 'root' })
export class SearchPreferenceService {

  private readonly localStorageService = inject(LocalStorageService);
  private readonly modeSubject: BehaviorSubject<SearchTriggerMode>;

  readonly mode$: Observable<SearchTriggerMode>;

  constructor() {
    const stored = this.localStorageService.get<string>(STORAGE_KEY);
    const initial: SearchTriggerMode = VALID_MODES.includes(stored as SearchTriggerMode)
      ? (stored as SearchTriggerMode)
      : 'instant';

    this.modeSubject = new BehaviorSubject<SearchTriggerMode>(initial);
    this.mode$ = this.modeSubject.asObservable();
  }

  get mode(): SearchTriggerMode {
    return this.modeSubject.value;
  }

  setMode(mode: SearchTriggerMode): void {
    this.modeSubject.next(mode);
    this.localStorageService.set(STORAGE_KEY, mode);
  }
}
