import {inject, Injectable} from '@angular/core';
import {IconService} from '../../shared/services/icon.service';
import {forkJoin, Observable, of} from 'rxjs';
import {catchError, map} from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class PostLoginInitializerService {

  private iconService = inject(IconService);

  initialize(): Observable<void> {
    return forkJoin([
      this.preloadIcons(),
    ]).pipe(
      map(() => undefined)
    );
  }

  private preloadIcons(): Observable<void> {
    return this.iconService.preloadAllIcons().pipe(
      catchError((err) => {
        console.error('Failed to preload icons:', err);
        return of(void 0);
      })
    );
  }
}
