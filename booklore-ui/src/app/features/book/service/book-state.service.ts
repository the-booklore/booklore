import {Injectable} from '@angular/core';
import {BehaviorSubject, Observable} from 'rxjs';
import {BookState} from '../model/state/book-state.model';

@Injectable({
  providedIn: 'root',
})
export class BookStateService {
  private bookStateSubject = new BehaviorSubject<BookState>({
    books: null,
    loaded: false,
    error: null,
  });

  public readonly bookState$ = this.bookStateSubject.asObservable();

  getCurrentBookState(): BookState {
    return this.bookStateSubject.value;
  }

  updateBookState(state: BookState): void {
    this.bookStateSubject.next(state);
  }

  updatePartialBookState(partialState: Partial<BookState>): void {
    this.bookStateSubject.next({
      ...this.bookStateSubject.value,
      ...partialState
    });
  }

  resetBookState(): void {
    this.bookStateSubject.next({
      books: null,
      loaded: true,
      error: null,
    });
  }
}

