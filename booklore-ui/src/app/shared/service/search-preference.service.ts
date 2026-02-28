import {inject, Injectable} from '@angular/core';
import {LocalStorageService} from './local-storage.service';

export type SearchTriggerMode = 'instant' | 'button';

const VALID_MODES: SearchTriggerMode[] = ['instant', 'button'];
const STORAGE_KEY = 'searchTriggerMode';

@Injectable({ providedIn: 'root' })
export class SearchPreferenceService {

  private readonly localStorageService = inject(LocalStorageService);
  private _mode: SearchTriggerMode;

  constructor() {
    const stored = this.localStorageService.get<string>(STORAGE_KEY);
    this._mode = VALID_MODES.includes(stored as SearchTriggerMode)
      ? (stored as SearchTriggerMode)
      : 'instant';
  }

  get mode(): SearchTriggerMode {
    return this._mode;
  }

  setMode(mode: SearchTriggerMode): void {
    this._mode = mode;
    this.localStorageService.set(STORAGE_KEY, mode);
  }
}
