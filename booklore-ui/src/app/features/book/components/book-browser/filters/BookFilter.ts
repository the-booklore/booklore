import {BookState} from '../../../model/state/book-state.model';
import {Observable} from 'rxjs';

export interface BookFilter {
  filter(bookState: BookState): Observable<BookState>;
}
