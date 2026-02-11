import {Book} from '../book.model';

export interface BookState {
  books: Book[] | null;
  loaded: boolean;
  error: string | null;
}
