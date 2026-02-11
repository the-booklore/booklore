import {Shelf} from '../shelf.model';

export interface ShelfState {
  shelves: Shelf[] | null;
  loaded: boolean;
  error: string | null;
}
