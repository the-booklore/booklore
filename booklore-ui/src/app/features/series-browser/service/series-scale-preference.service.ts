import {inject, Injectable} from '@angular/core';
import {Subject} from 'rxjs';
import {debounceTime} from 'rxjs/operators';
import {LocalStorageService} from '../../../shared/service/local-storage.service';

@Injectable({
  providedIn: 'root'
})
export class SeriesScalePreferenceService {

  private readonly DEBOUNCE_MS = 1000;
  private readonly STORAGE_KEY = 'seriesScalePreference';

  private readonly localStorageService = inject(LocalStorageService);

  private readonly scaleChangeSubject = new Subject<number>();

  scaleFactor = 1.0;

  constructor() {
    this.loadScaleFromStorage();

    this.scaleChangeSubject
      .pipe(debounceTime(this.DEBOUNCE_MS))
      .subscribe(scale => this.saveScalePreference(scale));
  }

  setScale(scale: number): void {
    this.scaleFactor = scale;
    this.scaleChangeSubject.next(scale);
  }

  private saveScalePreference(scale: number): void {
    this.localStorageService.set(this.STORAGE_KEY, scale);
  }

  private loadScaleFromStorage(): void {
    const saved = this.localStorageService.get<number>(this.STORAGE_KEY);
    if (saved !== null && !isNaN(saved)) {
      this.scaleFactor = saved;
    }
  }
}
