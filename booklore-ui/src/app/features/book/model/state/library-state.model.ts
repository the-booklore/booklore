import {Library} from '../library.model';

export interface LibraryState {
  libraries: Library[] | null;
  loaded: boolean;
  error: string | null;
}
