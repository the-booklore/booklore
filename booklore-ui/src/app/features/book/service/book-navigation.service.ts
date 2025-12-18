import {Injectable} from '@angular/core';
import {BehaviorSubject, Observable} from 'rxjs';

export interface BookNavigationState {
  bookIds: number[];
  currentIndex: number;
}

@Injectable({
  providedIn: 'root'
})
export class BookNavigationService {
  private navigationState$ = new BehaviorSubject<BookNavigationState | null>(null);
  private availableBookIds: number[] = [];

  setAvailableBookIds(bookIds: number[]): void {
    this.availableBookIds = bookIds;
  }

  getAvailableBookIds(): number[] {
    return this.availableBookIds;
  }

  setNavigationContext(bookIds: number[], currentBookId: number): void {
    const currentIndex = bookIds.indexOf(currentBookId);
    if (currentIndex !== -1) {
      this.navigationState$.next({bookIds, currentIndex});
    } else {
      this.navigationState$.next(null);
    }
  }

  getNavigationState(): Observable<BookNavigationState | null> {
    return this.navigationState$.asObservable();
  }

  canNavigatePrevious(): boolean {
    const state = this.navigationState$.value;
    return state !== null && state.currentIndex > 0;
  }

  canNavigateNext(): boolean {
    const state = this.navigationState$.value;
    return state !== null && state.currentIndex < state.bookIds.length - 1;
  }

  getPreviousBookId(): number | null {
    const state = this.navigationState$.value;
    if (state && state.currentIndex > 0) {
      return state.bookIds[state.currentIndex - 1];
    }
    return null;
  }

  getNextBookId(): number | null {
    const state = this.navigationState$.value;
    if (state && state.currentIndex < state.bookIds.length - 1) {
      return state.bookIds[state.currentIndex + 1];
    }
    return null;
  }

  updateCurrentBook(bookId: number): void {
    const state = this.navigationState$.value;
    if (state) {
      const newIndex = state.bookIds.indexOf(bookId);
      if (newIndex !== -1) {
        this.navigationState$.next({
          ...state,
          currentIndex: newIndex
        });
      }
    }
  }

  getCurrentPosition(): { current: number; total: number } | null {
    const state = this.navigationState$.value;
    if (state) {
      return {
        current: state.currentIndex + 1,
        total: state.bookIds.length
      };
    }
    return null;
  }
}
